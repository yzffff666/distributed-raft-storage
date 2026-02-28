package com.github.raftimpl.raft.example.server.machine;

import btree4j.BTreeException;
import com.github.raftimpl.raft.ConsensusNode;
import com.github.raftimpl.raft.StateMachine;
import com.github.raftimpl.raft.example.server.service.ExampleProto;
import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.storage.SegmentedLog;
import org.apache.commons.io.FileUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * LevelDB状态机实现
 * 基于LevelDB存储引擎的Raft状态机实现，提供持久化的键值存储功能
 * 支持快照创建、快照恢复、数据应用和数据查询等操作
 * 
 */
public class LevelDBStateMachine implements StateMachine {
    private static final Logger LOG = LoggerFactory.getLogger(LevelDBStateMachine.class);
    
    /** LevelDB数据库实例，用于存储键值对数据 */
    private DB database;
    
    /** Raft数据目录路径 */
    private final String dataDirectory;

    /**
     * 构造函数
     * 
     * @param dataDirectory Raft数据存储目录
     */
    public LevelDBStateMachine(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /**
     * 创建状态机快照
     * 将当前状态机数据和未包含在快照中的日志条目合并，生成新的快照
     * 
     * @param backupDir 旧快照目录
     * @param tmpSnapshotDataDir 新快照数据临时目录
     * @param raftNode Raft节点实例
     * @param localLastAppliedIndex 本地最后应用的日志索引
     */
    @Override
    public void writeSnap(String backupDir, String tmpSnapshotDataDir, ConsensusNode raftNode, long localLastAppliedIndex) {
        try {
            // 复制现有快照数据到临时目录
            File snapshotData = new File(backupDir + File.separator + "data");
            File tmpSnapshotData = new File(tmpSnapshotDataDir);
            FileUtils.copyDirectory(snapshotData, tmpSnapshotData);

            // 打开临时数据库
            Options options = new Options();
            DB tmpDB = Iq80DBFactory.factory.open(tmpSnapshotData, options);

            // 应用快照之后的日志条目到临时数据库
            SegmentedLog raftLog = raftNode.getRaftLog();
            for (long index = raftNode.getSnapshot().getMeta().getLastIncludedIndex() + 1;
                 index <= localLastAppliedIndex; index++) {
                RaftProto.LogEntry entry = raftLog.getEntry(index);
                if (entry.getType() == RaftProto.EntryType.ENTRY_TYPE_DATA) {
                    ExampleProto.SetRequest request = ExampleProto.SetRequest.parseFrom(entry.getData().toByteArray());
                    tmpDB.put(request.getKey().getBytes(), request.getValue().getBytes());
                }
            }
            LOG.info("Snapshot creation completed successfully");
            tmpDB.close();
        } catch (Exception e) {
            LOG.warn("writeSnapshot meet exception, msg={}", e.getMessage());
        }
    }

    /**
     * 从快照恢复状态机数据
     * 在节点启动时调用，将快照数据加载到状态机中
     * 
     * @param snapshotDir 快照数据目录
     */
    @Override
    public void readSnap(String snapshotDir) {
        try {
            // 关闭现有数据库连接
            if (database != null) {
                database.close();
                database = null;
            }
            
            // 准备数据目录
            String dataDir = dataDirectory + File.separator + "leveldb_data";
            File dataFile = new File(dataDir);
            if (dataFile.exists()) {
                FileUtils.deleteDirectory(dataFile);
            }
            
            // 复制快照数据到数据目录
            File snapshotFile = new File(snapshotDir);
            if (snapshotFile.exists()) {
                FileUtils.copyDirectory(snapshotFile, dataFile);
            }

            // 重新打开数据库
            Options options = new Options();
            database = Iq80DBFactory.factory.open(dataFile, options);
        } catch (Exception e) {
            LOG.warn("readSnap meet exception, msg={}", e.getMessage());
        }
    }

    /**
     * 将日志条目应用到状态机
     * 解析日志条目中的数据并更新状态机状态
     * 
     * @param content 日志条目的数据内容
     */
    @Override
    public void applyData(byte[] content) {
        try {
            if (database == null) {
                throw new BTreeException("database is closed, please wait for reopen");
            }
            // 解析设置请求并应用到数据库
            ExampleProto.SetRequest request = ExampleProto.SetRequest.parseFrom(content);
            database.put(request.getKey().getBytes(), request.getValue().getBytes());
        } catch (Exception e) {
            LOG.warn("applyData meet exception, msg={}", e.getMessage());
        }
    }

    /**
     * 从状态机读取数据
     * 根据键查询对应的值
     * 
     * @param dataBytes 键的字节数组
     * @return 对应的值的字节数组，如果不存在则返回null
     */
    @Override
    public byte[] get(byte[] dataBytes) {
        byte[] result = null;
        try {
            if (database == null) {
                throw new DBException("database is closed, please wait for reopen");
            }
            result = database.get(dataBytes);
        } catch (Exception e) {
            LOG.warn("read leveldb exception, msg={}", e.getMessage());
        }
        return result;
    }
}
