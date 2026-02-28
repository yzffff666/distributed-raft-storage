package com.github.raftimpl.raft.storage;

import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.util.FileIOUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Raft快照管理类
 * 负责管理Raft算法中的快照功能，包括快照的创建、读取、安装等操作
 * 快照用于压缩日志，提高系统性能和减少存储空间
 * 
 */
public class Snapshot {

    /**
     * 快照数据文件类
     * 封装快照数据文件的文件名和文件句柄
     */
    public class SnapshotDataFile {
        /** 文件名 */
        public String fileName;
        /** 随机访问文件句柄 */
        public RandomAccessFile randomAccessFile;
    }

    private static final Logger LOG = LoggerFactory.getLogger(Snapshot.class);
    
    /** 快照存储目录路径 */
    private String snapshotDir;
    
    /** 快照元数据，包含最后包含的索引、任期和配置信息 */
    private RaftProto.SnapshotMetaData metaData;
    
    /** 
     * 表示是否正在安装快照
     * leader向follower安装时，leader和follower同时处于installSnapshot状态
     */
    private AtomicBoolean isInstallSnapshot = new AtomicBoolean(false);
    
    /** 表示节点自己是否在对状态机做快照 */
    private AtomicBoolean isTakeSnapshot = new AtomicBoolean(false);
    
    /** 快照操作的同步锁 */
    private Lock lock = new ReentrantLock();

    /**
     * 构造函数
     * 初始化快照目录和数据目录
     * 
     * @param raftDataDir Raft数据根目录
     */
    public Snapshot(String raftDataDir) {
        this.snapshotDir = raftDataDir + File.separator + "snapshot";
        String snapshotDataDir = snapshotDir + File.separator + "data";
        File file = new File(snapshotDataDir);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 重新加载快照元数据
     * 从磁盘读取快照元数据，如果不存在则创建空的元数据
     */
    public void reload() {
        metaData = this.readMeta();
        if (metaData == null) {
            metaData = RaftProto.SnapshotMetaData.newBuilder().build();
        }
    }

    /**
     * 打开快照数据目录下的所有文件
     * 如果是软链接，需要打开实际文件句柄
     * 
     * @return 文件名以及文件句柄的映射表，按文件名排序
     */
    public TreeMap<String, SnapshotDataFile> openSnapshotFiles() {
        TreeMap<String, SnapshotDataFile> fileMap = new TreeMap<>();
        String dataDirectory = snapshotDir + File.separator + "data";
        try {
            // 解析软链接，获取实际路径
            Path dataPath = FileSystems.getDefault().getPath(dataDirectory);
            dataPath = dataPath.toRealPath();
            dataDirectory = dataPath.toString();
            
            // 获取目录下所有文件并排序
            List<String> dataFiles = FileIOUtils.getSortedFilesInDir(dataDirectory, dataDirectory);
            for (String dataFileName : dataFiles) {
                RandomAccessFile fileHandle = FileIOUtils.openFile(dataDirectory, dataFileName, "r");
                SnapshotDataFile snapFile = new SnapshotDataFile();
                snapFile.fileName = dataFileName;
                snapFile.randomAccessFile = fileHandle;
                fileMap.put(dataFileName, snapFile);
            }
        } catch (IOException ex) {
            LOG.warn("readSnapshotDataFiles exception:", ex);
            throw new RuntimeException(ex);
        }
        return fileMap;
    }

    /**
     * 关闭快照文件句柄
     * 释放所有打开的快照数据文件资源
     * 
     * @param snapshotDataFileMap 快照数据文件映射表
     */
    public void closeSnapshotFiles(TreeMap<String, SnapshotDataFile> snapshotDataFileMap) {
        for (Map.Entry<String, SnapshotDataFile> entry : snapshotDataFileMap.entrySet()) {
            try {
                entry.getValue().randomAccessFile.close();
            } catch (IOException ex) {
                LOG.warn("close snapshot files exception:", ex);
            }
        }
    }

    /**
     * 读取快照元数据
     * 从磁盘文件中读取快照的元数据信息
     * 
     * @return 快照元数据，如果文件不存在则返回null
     */
    public RaftProto.SnapshotMetaData readMeta() {
        String fileName = snapshotDir + File.separator + "metadata";
        File file = new File(fileName);
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            RaftProto.SnapshotMetaData metadata = FileIOUtils.readProtoFromFile(
                    randomAccessFile, RaftProto.SnapshotMetaData.class);
            return metadata;
        } catch (IOException ex) {
            LOG.warn("meta file not exist, name={}", fileName);
            return null;
        }
    }

    /**
     * 更新快照元数据
     * 将新的快照元数据写入指定目录
     * 
     * @param directory 目标目录
     * @param lastIncludedIndex 快照包含的最后一个日志索引
     * @param lastIncludedTerm 快照包含的最后一个日志任期
     * @param configuration 快照时的集群配置
     */
    public void updateMeta(String directory,
                               Long lastIncludedIndex,
                               Long lastIncludedTerm,
                               RaftProto.Configuration configuration) {
        // 构建快照元数据
        RaftProto.SnapshotMetaData snapMetaData = RaftProto.SnapshotMetaData.newBuilder()
                .setLastIncludedIndex(lastIncludedIndex)
                .setLastIncludedTerm(lastIncludedTerm)
                .setConfiguration(configuration).build();
        
        String metaFilePath = directory + File.separator + "metadata";
        RandomAccessFile dataFile = null;
        try {
            // 确保目录存在
            File directoryFile = new File(directory);
            if (!directoryFile.exists()) {
                directoryFile.mkdirs();
            }

            // 删除旧的元数据文件并创建新文件
            File metaFile = new File(metaFilePath);
            if (metaFile.exists()) {
                FileUtils.forceDelete(metaFile);
            }
            metaFile.createNewFile();
            
            // 写入元数据
            dataFile = new RandomAccessFile(metaFile, "rw");
            FileIOUtils.writeProtoToFile(dataFile, snapMetaData);
        } catch (IOException ex) {
            LOG.warn("update meta file failed, name={}", metaFilePath);
        } finally {
            FileIOUtils.closeFile(dataFile);
        }
    }

    /**
     * 获取快照元数据
     * 
     * @return 当前的快照元数据
     */
    public RaftProto.SnapshotMetaData getMeta() {
        return metaData;
    }

    /**
     * 获取快照目录路径
     * 
     * @return 快照目录路径
     */
    public String getSnapshotDir() {
        return snapshotDir;
    }

    /**
     * 获取安装快照状态标志
     * 
     * @return 是否正在安装快照的原子布尔值
     */
    public AtomicBoolean getIsinstallSnap() {
        return isInstallSnapshot;
    }

    /**
     * 获取创建快照状态标志
     * 
     * @return 是否正在创建快照的原子布尔值
     */
    public AtomicBoolean getIsTakeSnap() {
        return isTakeSnapshot;
    }

    /**
     * 获取快照操作锁
     * 
     * @return 快照操作的同步锁
     */
    public Lock getLock() {
        return lock;
    }
}
