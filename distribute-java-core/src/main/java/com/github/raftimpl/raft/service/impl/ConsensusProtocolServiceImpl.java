package com.github.raftimpl.raft.service.impl;

import com.github.raftimpl.raft.ConsensusNode;
import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.service.ConsensusProtocolService;
import com.github.raftimpl.raft.util.ConfigurationUtils;
import com.github.raftimpl.raft.util.FileIOUtils;
import com.googlecode.protobuf.format.JsonFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * 共识协议服务实现类
 * 实现Raft共识算法的核心逻辑，包括预投票、投票、日志复制等
 * 
 */
public class ConsensusProtocolServiceImpl implements ConsensusProtocolService {

    /** 日志记录器 */
    private static final Logger LOG = LoggerFactory.getLogger(ConsensusProtocolServiceImpl.class);
    private static final JsonFormat PRINTER = new JsonFormat();

    /** Raft节点实例，包含节点的所有状态和配置信息 */
    private ConsensusNode consensusNode;

    /**
     * 构造函数
     * 
     * @param consensusNode 共识节点实例
     */
    public ConsensusProtocolServiceImpl(ConsensusNode consensusNode) {
        this.consensusNode = consensusNode;
    }

    /**
     * 预投票RPC实现
     * 在正式投票前进行预投票，用于减少不必要的选举轮次
     * 预投票不会改变节点状态，只是检查是否有可能获得投票
     * 
     * @param request 预投票请求
     * @return 预投票响应
     */
    @Override
    public RaftProto.VoteResponse preElection(RaftProto.VoteRequest request) {
        consensusNode.getLock().lock();
        try {
            RaftProto.VoteResponse.Builder responseBuilder = RaftProto.VoteResponse.newBuilder();
            responseBuilder.setGranted(false);
            responseBuilder.setTerm(consensusNode.getCurrentTerm());
            
            // 检查请求节点是否在集群配置中
            if (!ConfigurationUtils.containsStorageServer(consensusNode.getConfig(), request.getServerId())) {
                return responseBuilder.build();
            }
            
            // 检查请求的任期是否过时
            if (request.getTerm() < consensusNode.getCurrentTerm()) {
                return responseBuilder.build();
            }
            
            // 检查候选者的日志是否至少和当前节点一样新
            boolean isLogOk = request.getLastLogTerm() > consensusNode.getLastLogTerm()
                    || (request.getLastLogTerm() == consensusNode.getLastLogTerm()
                    && request.getLastLogIndex() >= consensusNode.getRaftLog().getLastLogIndex());
            if (!isLogOk) {
                return responseBuilder.build();
            } else {
                responseBuilder.setGranted(true);
                responseBuilder.setTerm(consensusNode.getCurrentTerm());
            }
            
            LOG.info("preVote request from server {} " +
                            "in term {} (my term is {}), granted={}",
                    request.getServerId(), request.getTerm(),
                    consensusNode.getCurrentTerm(), responseBuilder.getGranted());
            return responseBuilder.build();
        } finally {
            consensusNode.getLock().unlock();
        }
    }

    /**
     * 请求投票RPC实现
     * 处理候选者的投票请求，根据Raft算法规则决定是否投票
     * 
     * @param request 投票请求
     * @return 投票响应
     */
    @Override
    public RaftProto.VoteResponse requestElection(RaftProto.VoteRequest request) {
        consensusNode.getLock().lock();
        try {
            RaftProto.VoteResponse.Builder responseBuilder = RaftProto.VoteResponse.newBuilder();
            responseBuilder.setGranted(false);
            responseBuilder.setTerm(consensusNode.getCurrentTerm());
            
            // 检查请求节点是否在集群配置中
            if (!ConfigurationUtils.containsStorageServer(consensusNode.getConfig(), request.getServerId())) {
                return responseBuilder.build();
            }
            
            // 如果请求的任期小于当前任期，拒绝投票
            if (request.getTerm() < consensusNode.getCurrentTerm()) {
                return responseBuilder.build();
            }
            
            // 如果请求的任期大于当前任期，转为follower
            if (request.getTerm() > consensusNode.getCurrentTerm()) {
                consensusNode.stepDown(request.getTerm());
            }
            
            // 检查候选者的日志是否至少和当前节点一样新
            boolean logIsOk = request.getLastLogTerm() > consensusNode.getLastLogTerm()
                    || (request.getLastLogTerm() == consensusNode.getLastLogTerm()
                    && request.getLastLogIndex() >= consensusNode.getRaftLog().getLastLogIndex());
            
            // 如果还没有投票且候选者日志足够新，则投票
            if (consensusNode.getVotedFor() == 0 && logIsOk) {
                consensusNode.stepDown(request.getTerm());
                consensusNode.setVotedFor(request.getServerId());
                consensusNode.getRaftLog().updateMeta(consensusNode.getCurrentTerm(), consensusNode.getVotedFor(), null, null);
                responseBuilder.setGranted(true);
                responseBuilder.setTerm(consensusNode.getCurrentTerm());
            }
            
            LOG.info("RequestVote request from server {} " +
                            "in term {} (my term is {}), granted={}",
                    request.getServerId(), request.getTerm(),
                    consensusNode.getCurrentTerm(), responseBuilder.getGranted());
            return responseBuilder.build();
        } finally {
            consensusNode.getLock().unlock();
        }
    }

    /**
     * 追加日志条目RPC实现
     * 处理Leader发送的日志复制请求，也用作心跳机制
     * 这是Raft算法中最复杂的RPC，负责日志一致性保证
     * 
     * @param request 追加日志请求
     * @return 追加日志响应
     */
    @Override
    public RaftProto.AppendEntriesResponse replicateEntries(RaftProto.AppendEntriesRequest request) {
        consensusNode.getLock().lock();
        try {
            RaftProto.AppendEntriesResponse.Builder responseBuilder
                    = RaftProto.AppendEntriesResponse.newBuilder();
            responseBuilder.setTerm(consensusNode.getCurrentTerm());
            responseBuilder.setResCode(RaftProto.ResCode.RES_CODE_FAIL);
            responseBuilder.setLastLogIndex(consensusNode.getRaftLog().getLastLogIndex());
            
            // 如果请求的任期小于当前任期，拒绝请求
            if (request.getTerm() < consensusNode.getCurrentTerm()) {
                return responseBuilder.build();
            }
            
            // 转为follower状态
            consensusNode.stepDown(request.getTerm());
            
            // 设置或验证leader身份
            if (consensusNode.getLeaderId() == 0) {
                consensusNode.setLeaderId(request.getServerId());
                LOG.info("new leaderId={}, conf={}",
                        consensusNode.getLeaderId(),
                        PRINTER.printToString(consensusNode.getConfig()));
            }
            
            // 检查是否有多个leader冲突
            if (consensusNode.getLeaderId() != request.getServerId()) {
                LOG.warn("Another peer={} declares that it is the leader " +
                                "at term={} which was occupied by leader={}",
                        request.getServerId(), request.getTerm(), consensusNode.getLeaderId());
                consensusNode.stepDown(request.getTerm() + 1);
                responseBuilder.setResCode(RaftProto.ResCode.RES_CODE_FAIL);
                responseBuilder.setTerm(request.getTerm() + 1);
                return responseBuilder.build();
            }

            // 检查日志一致性：prevLogIndex不能超过当前最后日志索引
            if (request.getPrevLogIndex() > consensusNode.getRaftLog().getLastLogIndex()) {
                LOG.info("Rejecting AppendEntries RPC would leave gap, " +
                        "request prevLogIndex={}, my lastLogIndex={}",
                        request.getPrevLogIndex(), consensusNode.getRaftLog().getLastLogIndex());
                return responseBuilder.build();
            }
            
            // 检查日志一致性：prevLogIndex处的任期必须匹配
            if (request.getPrevLogIndex() >= consensusNode.getRaftLog().getFirstLogIndex()
                    && consensusNode.getRaftLog().getEntryTerm(request.getPrevLogIndex())
                    != request.getPrevLogTerm()) {
                LOG.info("Rejecting AppendEntries RPC: terms don't agree, " +
                        "request prevLogTerm={} in prevLogIndex={}, my is {}",
                        request.getPrevLogTerm(), request.getPrevLogIndex(),
                        consensusNode.getRaftLog().getEntryTerm(request.getPrevLogIndex()));
                Validate.isTrue(request.getPrevLogIndex() > 0);
                responseBuilder.setLastLogIndex(request.getPrevLogIndex() - 1);
                return responseBuilder.build();
            }

            // 处理心跳请求（没有日志条目）
            if (request.getEntriesCount() == 0) {
                LOG.debug("heartbeat request from peer={} at term={}, my term={}",
                        request.getServerId(), request.getTerm(), consensusNode.getCurrentTerm());
                responseBuilder.setResCode(RaftProto.ResCode.RES_CODE_SUCCESS);
                responseBuilder.setTerm(consensusNode.getCurrentTerm());
                responseBuilder.setLastLogIndex(consensusNode.getRaftLog().getLastLogIndex());
                advanceCommitIndex(request);
                return responseBuilder.build();
            }

            // 处理日志条目追加
            responseBuilder.setResCode(RaftProto.ResCode.RES_CODE_SUCCESS);
            List<RaftProto.LogEntry> entries = new ArrayList<>();
            long index = request.getPrevLogIndex();
            
            // 遍历请求中的日志条目
            for (RaftProto.LogEntry entry : request.getEntriesList()) {
                index++;
                if (index < consensusNode.getRaftLog().getFirstLogIndex()) {
                    continue;
                }
                
                // 如果本地已有该索引的日志条目
                if (consensusNode.getRaftLog().getLastLogIndex() >= index) {
                    // 如果任期相同，跳过
                    if (consensusNode.getRaftLog().getEntryTerm(index) == entry.getTerm()) {
                        continue;
                    }
                    // 如果任期不同，截断后续日志
                    long lastIndexKept = index - 1;
                    consensusNode.getRaftLog().truncateSuffix(lastIndexKept);
                }
                entries.add(entry);
            }
            
            // 追加新的日志条目
            consensusNode.getRaftLog().append(entries);
            responseBuilder.setLastLogIndex(consensusNode.getRaftLog().getLastLogIndex());

            // 更新提交索引
            advanceCommitIndex(request);
            
            LOG.info("AppendEntries request from server {} " +
                            "in term {} (my term is {}), entryCount={} resCode={}",
                    request.getServerId(), request.getTerm(), consensusNode.getCurrentTerm(),
                    request.getEntriesCount(), responseBuilder.getResCode());
            return responseBuilder.build();
        } finally {
            consensusNode.getLock().unlock();
        }
    }

    @Override
    public RaftProto.InstallSnapshotResponse deploySnapshot(RaftProto.InstallSnapshotRequest request) {
        RaftProto.InstallSnapshotResponse.Builder responseBuilder
                = RaftProto.InstallSnapshotResponse.newBuilder();
        responseBuilder.setResCode(RaftProto.ResCode.RES_CODE_FAIL);

        consensusNode.getLock().lock();
        try {
            responseBuilder.setTerm(consensusNode.getCurrentTerm());
            if (request.getTerm() < consensusNode.getCurrentTerm()) {
                return responseBuilder.build();
            }
            consensusNode.stepDown(request.getTerm());
            if (consensusNode.getLeaderId() == 0) {
                consensusNode.setLeaderId(request.getServerId());
                LOG.info("new leaderId={}, conf={}",
                        consensusNode.getLeaderId(),
                        PRINTER.printToString(consensusNode.getConfig()));
            }
        } finally {
            consensusNode.getLock().unlock();
        }

        if (consensusNode.getSnapshot().getIsTakeSnap().get()) {
            LOG.warn("alreay in take snapshot, do not handle install snapshot request now");
            return responseBuilder.build();
        }

        consensusNode.getSnapshot().getIsinstallSnap().set(true);
        RandomAccessFile randomAccessFile = null;
        consensusNode.getSnapshot().getLock().lock();
        try {
            // write snapshot data to local
            String tmpSnapshotDir = consensusNode.getSnapshot().getSnapshotDir() + ".tmp";
            File file = new File(tmpSnapshotDir);
            if (request.getIsFirst()) {
                if (file.exists()) {
                    file.delete();
                }
                file.mkdir();
                LOG.info("begin accept install snapshot request from serverId={}", request.getServerId());
                consensusNode.getSnapshot().updateMeta(tmpSnapshotDir,
                        request.getSnapshotMetaData().getLastIncludedIndex(),
                        request.getSnapshotMetaData().getLastIncludedTerm(),
                        request.getSnapshotMetaData().getConfig());
            }
            // write to file
            String currentDataDirName = tmpSnapshotDir + File.separator + "data";
            File currentDataDir = new File(currentDataDirName);
            if (!currentDataDir.exists()) {
                currentDataDir.mkdirs();
            }

            String currentDataFileName = currentDataDirName + File.separator + request.getFileName();
            File currentDataFile = new File(currentDataFileName);
            // 文件名可能是个相对路径，比如topic/0/message.txt
            if (!currentDataFile.getParentFile().exists()) {
                currentDataFile.getParentFile().mkdirs();
            }
            if (!currentDataFile.exists()) {
                currentDataFile.createNewFile();
            }
            randomAccessFile = FileIOUtils.openFile(
                    tmpSnapshotDir + File.separator + "data",
                    request.getFileName(), "rw");
            randomAccessFile.seek(request.getOffset());
            randomAccessFile.write(request.getData().toByteArray());
            // move tmp dir to snapshot dir if this is the last package
            if (request.getIsLast()) {
                File snapshotDirFile = new File(consensusNode.getSnapshot().getSnapshotDir());
                if (snapshotDirFile.exists()) {
                    FileUtils.deleteDirectory(snapshotDirFile);
                }
                FileUtils.moveDirectory(new File(tmpSnapshotDir), snapshotDirFile);
            }
            responseBuilder.setResCode(RaftProto.ResCode.RES_CODE_SUCCESS);
            LOG.info("install snapshot request from server {} " +
                            "in term {} (my term is {}), resCode={}",
                    request.getServerId(), request.getTerm(),
                    consensusNode.getCurrentTerm(), responseBuilder.getResCode());
        } catch (IOException ex) {
            LOG.warn("when handle installSnapshot request, meet exception:", ex);
        } finally {
            FileIOUtils.closeFile(randomAccessFile);
            consensusNode.getSnapshot().getLock().unlock();
        }

        if (request.getIsLast() && responseBuilder.getResCode() == RaftProto.ResCode.RES_CODE_SUCCESS) {
            // apply state machine
            // TODO: make this async
            String snapshotDataDir = consensusNode.getSnapshot().getSnapshotDir() + File.separator + "data";
            consensusNode.getStateMachine().readSnap(snapshotDataDir);
            long lastSnapshotIndex;
            // 重新加载snapshot
            consensusNode.getSnapshot().getLock().lock();
            try {
                consensusNode.getSnapshot().reload();
                lastSnapshotIndex = consensusNode.getSnapshot().getMeta().getLastIncludedIndex();
            } finally {
                consensusNode.getSnapshot().getLock().unlock();
            }

            // discard old log entries
            consensusNode.getLock().lock();
            try {
                consensusNode.getRaftLog().truncatePrefix(lastSnapshotIndex + 1);
            } finally {
                consensusNode.getLock().unlock();
            }
            LOG.info("end accept install snapshot request from serverId={}", request.getServerId());
        }

        if (request.getIsLast()) {
            consensusNode.getSnapshot().getIsinstallSnap().set(false);
        }

        return responseBuilder.build();
    }

    // in lock, for follower
    private void advanceCommitIndex(RaftProto.AppendEntriesRequest request) {
        long newCommitIndex = Math.min(request.getCommitIndex(),
                request.getPrevLogIndex() + request.getEntriesCount());
        consensusNode.setCommitIndex(newCommitIndex);
        consensusNode.getRaftLog().updateMeta(null,null, null, newCommitIndex);
        if (consensusNode.getLastAppliedIndex() < consensusNode.getCommitIndex()) {
            // apply state machine
            for (long index = consensusNode.getLastAppliedIndex() + 1;
                 index <= consensusNode.getCommitIndex(); index++) {
                RaftProto.LogEntry entry = consensusNode.getRaftLog().getEntry(index);
                if (entry != null) {
                    if (entry.getType() == RaftProto.EntryType.ENTRY_TYPE_DATA) {
                        consensusNode.getStateMachine().applyData(entry.getData().toByteArray());
                    } else if (entry.getType() == RaftProto.EntryType.ENTRY_TYPE_CONFIGURATION) {
                        consensusNode.applyConfig(entry);
                    }
                }
                consensusNode.setLastAppliedIndex(index);
            }
        }
        // 在Follower-Read（Read Index）下，Follower会进入等待条件，等待日志应用到复制状态机，因此需要唤醒等待线程
        consensusNode.getCommitIndexCondition().signalAll();
    }

    @Override
    public RaftProto.GetLeaderCommitIndexResponse getLeaderCommitIndex(RaftProto.GetLeaderCommitIndexRequest request) {
        RaftProto.GetLeaderCommitIndexResponse.Builder responseBuilder = RaftProto.GetLeaderCommitIndexResponse.newBuilder();
        consensusNode.getLock().lock();
        long commitIndex;
        try {
            commitIndex = consensusNode.getCommitIndex();
        } finally {
            consensusNode.getLock().unlock();
        }
        responseBuilder.setCommitIndex(commitIndex);
        return responseBuilder.build();
    }
}
