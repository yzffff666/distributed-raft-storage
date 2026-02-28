package com.github.raftimpl.raft;

import com.baidu.brpc.client.RpcCallback;
import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.storage.SegmentedLog;
import com.github.raftimpl.raft.util.ConfigurationUtils;
import com.google.protobuf.ByteString;
import com.github.raftimpl.raft.storage.Snapshot;
import com.google.protobuf.InvalidProtocolBufferException;
import com.googlecode.protobuf.format.JsonFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * 该类是共识算法核心类，主要有如下功能：
 * 1、保存共识节点核心数据（节点状态信息、日志信息、snapshot等），
 * 2、共识节点向别的节点发起rpc请求相关函数
 * 3、共识节点定时器：主节点心跳定时器、发起选举定时器。
 */
public class ConsensusNode {

    public enum NodeState {
        STATE_FOLLOWER,
        STATE_PRE_CANDIDATE,
        STATE_CANDIDATE,
        STATE_LEADER
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConsensusNode.class);
    private static final JsonFormat jsonFormat = new JsonFormat();

    private ConsensusConfiguration nodeOptions;
    private RaftProto.Configuration clusterConfig;
    private ConcurrentMap<Integer, ClusterNode> peerNodes = new ConcurrentHashMap<>();
    private RaftProto.Server currentServer;
    private StateMachine stateMachine;
    private SegmentedLog logManager;
    private Snapshot snapshotManager;

    private NodeState nodeState = NodeState.STATE_FOLLOWER;
    // 服务器最后一次知道的任期号（初始化为 0，持续递增）
    private long currentTerm;
    // 在当前获得选票的候选人的Id
    private int votedFor;
    private int leaderId; // leader节点id
    // 已知的最大的已经被提交的日志条目的索引值
    private long commitIndex;
    // 最后被应用到状态机的日志条目索引值（初始化为 0，持续递增）
    private volatile long lastAppliedIndex;

    private Lock stateLock = new ReentrantLock();
    private Condition commitCondition = stateLock.newCondition();
    private Condition syncCondition = stateLock.newCondition();

    private ExecutorService taskExecutor;
    private ScheduledExecutorService timerExecutor;
    private ScheduledFuture electionTimer;
    private ScheduledFuture heartbeatTimer;

    public ConsensusNode(ConsensusConfiguration nodeOptions,
                    List<RaftProto.Server> servers,
                    RaftProto.Server currentServer,
                    StateMachine stateMachine) {
        this.nodeOptions = nodeOptions;
        RaftProto.Configuration.Builder configBuilder = RaftProto.Configuration.newBuilder();
        for (RaftProto.Server server : servers) {
            configBuilder.addServers(server);
        }
        clusterConfig = configBuilder.build();

        this.currentServer = currentServer;
        this.stateMachine = stateMachine;

        // load log and snapshot
        logManager = new SegmentedLog(nodeOptions.getDataDir(), nodeOptions.getMaxSegmentFileSize());
        snapshotManager = new Snapshot(nodeOptions.getDataDir());
        snapshotManager.reload();

        currentTerm = logManager.getMeta().getCurrentTerm();
        votedFor = logManager.getMeta().getVotedFor();
        commitIndex = Math.max(snapshotManager.getMeta().getLastIncludedIndex(), 
                             logManager.getMeta().getCommitIndex());
        // discard old log entries
        if (snapshotManager.getMeta().getLastIncludedIndex() > 0
                && logManager.getFirstLogIndex() <= snapshotManager.getMeta().getLastIncludedIndex()) {
            logManager.truncatePrefix(snapshotManager.getMeta().getLastIncludedIndex() + 1);
        }
        // apply state machine
        RaftProto.Configuration snapConfig = snapshotManager.getMeta().getConfig();
        if (snapConfig.getServersCount() > 0) {
            clusterConfig = snapConfig;
        }
        String snapshotDataPath = snapshotManager.getSnapshotDir() + File.separator + "data";
        stateMachine.readSnap(snapshotDataPath);
        for (long index = snapshotManager.getMeta().getLastIncludedIndex() + 1;
             index <= commitIndex; index++) {
            RaftProto.LogEntry entry = logManager.getEntry(index);
            if (entry.getType() == RaftProto.EntryType.ENTRY_TYPE_DATA) {
                stateMachine.applyData(entry.getData().toByteArray());
            } else if (entry.getType() == RaftProto.EntryType.ENTRY_TYPE_CONFIGURATION) {
                applyConfig(entry);
            }
        }
        lastAppliedIndex = commitIndex;
    }

    public void init() {
        for (RaftProto.Server server : clusterConfig.getServersList()) {
            if (!peerNodes.containsKey(server.getServerId())
                    && server.getServerId() != currentServer.getServerId()) {
                ClusterNode peer = new ClusterNode(server);
                peer.setNextIndex(logManager.getLastLogIndex() + 1);
                peerNodes.put(server.getServerId(), peer);
            }
        }

        // init thread pool
        taskExecutor = new ThreadPoolExecutor(
                nodeOptions.getRaftConsensusThreadNum(),
                nodeOptions.getRaftConsensusThreadNum(),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
        timerExecutor = Executors.newScheduledThreadPool(2);
        timerExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                takeSnap();
            }
        }, nodeOptions.getBackupIntervalSeconds(), nodeOptions.getBackupIntervalSeconds(), TimeUnit.SECONDS);
        // start election
        resetElectionTimer();
    }

    // client set command
    public boolean replicate(byte[] data, RaftProto.EntryType entryType) {
        stateLock.lock();
        long newLastLogIndex = 0;
        try {
            if (nodeState != NodeState.STATE_LEADER) {
                LOG.debug("I'm not the leader");
                return false;
            }
            RaftProto.LogEntry logEntry = RaftProto.LogEntry.newBuilder()
                    .setTerm(currentTerm)
                    .setType(entryType)
                    .setData(ByteString.copyFrom(data)).build();
            List<RaftProto.LogEntry> entries = new ArrayList<>();
            entries.add(logEntry);
            newLastLogIndex = logManager.append(entries);
 //           logManager.updateMeta(currentTerm, null, logManager.getFirstLogIndex());

            for (RaftProto.Server server : clusterConfig.getServersList()) {
                final ClusterNode peer = peerNodes.get(server.getServerId());
                taskExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        appendEntries(peer);
                    }
                });
            }

            if (nodeOptions.isAsyncWrite()) {
                // 主节点写成功后，就返回。
                return true;
            }

            // sync wait commitIndex >= newLastLogIndex
            long startTime = System.currentTimeMillis();
            while (lastAppliedIndex < newLastLogIndex) {
                if (System.currentTimeMillis() - startTime >= nodeOptions.getMaxAwaitTimeout()) {
                    break;
                }
                commitCondition.await(nodeOptions.getMaxAwaitTimeout(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            stateLock.unlock();
        }
        LOG.debug("lastAppliedIndex={} newLastLogIndex={}", lastAppliedIndex, newLastLogIndex);
        if (lastAppliedIndex < newLastLogIndex) {
            return false;
        }
        return true;
    }

    public boolean appendEntries(ClusterNode peer) {
        RaftProto.AppendEntriesRequest.Builder requestBuilder = RaftProto.AppendEntriesRequest.newBuilder();
        long prevLogIndex;
        long numEntries;

        boolean isNeedInstallSnapshot = false;
        stateLock.lock();
        try {
            long firstLogIndex = logManager.getFirstLogIndex();
            if (peer.getNextIndex() < firstLogIndex) {
                isNeedInstallSnapshot = true;
            }
        } finally {
            stateLock.unlock();
        }

        LOG.debug("is need snapshot={}, peer={}", isNeedInstallSnapshot, peer.getStorageServer().getServerId());
        if (isNeedInstallSnapshot) {
            if (!installSnap(peer)) {
                return false;
            }
        }

        long lastSnapshotIndex;
        long lastSnapshotTerm;
        snapshotManager.getLock().lock();
        try {
            lastSnapshotIndex = snapshotManager.getMeta().getLastIncludedIndex();
            lastSnapshotTerm = snapshotManager.getMeta().getLastIncludedTerm();
        } finally {
            snapshotManager.getLock().unlock();
        }

        stateLock.lock();
        try {
            long firstLogIndex = logManager.getFirstLogIndex();
            Validate.isTrue(peer.getNextIndex() >= firstLogIndex);
            prevLogIndex = peer.getNextIndex() - 1;
            long prevLogTerm;
            if (prevLogIndex == 0) {
                prevLogTerm = 0;
            } else if (prevLogIndex == lastSnapshotIndex) {
                prevLogTerm = lastSnapshotTerm;
            } else {
                prevLogTerm = logManager.getEntryTerm(prevLogIndex);
            }
            requestBuilder.setServerId(currentServer.getServerId());
            requestBuilder.setTerm(currentTerm);
            requestBuilder.setPrevLogTerm(prevLogTerm);
            requestBuilder.setPrevLogIndex(prevLogIndex);
            numEntries = packEntries(peer.getNextIndex(), requestBuilder);
            requestBuilder.setCommitIndex(Math.min(commitIndex, prevLogIndex + numEntries));
        } finally {
            stateLock.unlock();
        }

        RaftProto.AppendEntriesRequest request = requestBuilder.build();
        RaftProto.AppendEntriesResponse response = null;
        try {
            // 使用同步调用发送日志复制请求
            response = peer.getConsensusService().replicateEntries(request);
        } catch (Exception ex) {
            LOG.warn("appendEntries with peer[{}:{}] failed, exception: {}",
                    peer.getStorageServer().getEndpoint().getHost(),
                    peer.getStorageServer().getEndpoint().getPort(),
                    ex.getMessage());
        }

        stateLock.lock();
        try {
            if (response == null) {
                LOG.warn("appendEntries with peer[{}:{}] failed",
                        peer.getStorageServer().getEndpoint().getHost(),
                        peer.getStorageServer().getEndpoint().getPort());
                if (!ConfigurationUtils.containsStorageServer(clusterConfig, peer.getStorageServer().getServerId())) {
                    peerNodes.remove(peer.getStorageServer().getServerId());
                    peer.getRpcClient().stop();
                }
                return false;
            }
            LOG.info("AppendEntries response[{}] from server {} " +
                            "in term {} (my term is {})",
                    response.getResCode(), peer.getStorageServer().getServerId(),
                    response.getTerm(), currentTerm);

            if (response.getTerm() > currentTerm) {
                stepDown(response.getTerm());
            } else {
                if (response.getResCode() == RaftProto.ResCode.RES_CODE_SUCCESS) {
                    peer.setMatchIndex(prevLogIndex + numEntries);
                    peer.setNextIndex(peer.getMatchIndex() + 1);
                    if (ConfigurationUtils.containsStorageServer(clusterConfig, peer.getStorageServer().getServerId())) {
                        advanceCommitIndex();
                    } else {
                        if (logManager.getLastLogIndex() - peer.getMatchIndex() <= nodeOptions.getCatchupMargin()) {
                            LOG.debug("peer catch up the leader");
                            peer.setCatchUp(true);
                            // signal the caller thread
                            syncCondition.signalAll();
                        }
                    }
                } else {
                    peer.setNextIndex(response.getLastLogIndex() + 1);
                }
            }
        } finally {
            stateLock.unlock();
        }

        return true;
    }

    // in lock
    public void stepDown(long newTerm) {
        if (currentTerm > newTerm) {
            LOG.error("can't be happened");
            return;
        }
        if (currentTerm < newTerm) {
            currentTerm = newTerm;
            leaderId = 0;
            votedFor = 0;
            logManager.updateMeta(currentTerm, votedFor, null, null);
        }
        nodeState = NodeState.STATE_FOLLOWER;
        // stop heartbeat
        if (heartbeatTimer != null && !heartbeatTimer.isDone()) {
            heartbeatTimer.cancel(true);
        }
        resetElectionTimer();
    }

    public void takeSnap() {
        if (snapshotManager.getIsinstallSnap().get()) {
            LOG.info("already in install snapshot, ignore take snapshot");
            return;
        }

        snapshotManager.getIsTakeSnap().compareAndSet(false, true);
        try {
            long localLastAppliedIndex;
            long lastAppliedTerm = 0;
            RaftProto.Configuration.Builder localConfiguration = RaftProto.Configuration.newBuilder();
            stateLock.lock();
            try {
                if (logManager.getTotalSize() < nodeOptions.getSnapshotMinLogSize()) {
                    return;
                }
                if (lastAppliedIndex <= snapshotManager.getMeta().getLastIncludedIndex()) {
                    return;
                }
                localLastAppliedIndex = lastAppliedIndex;
                if (lastAppliedIndex >= logManager.getFirstLogIndex()
                        && lastAppliedIndex <= logManager.getLastLogIndex()) {
                    lastAppliedTerm = logManager.getEntryTerm(lastAppliedIndex);
                }
                localConfiguration.mergeFrom(clusterConfig);
            } finally {
                stateLock.unlock();
            }

            boolean success = false;
            snapshotManager.getLock().lock();
            try {
                LOG.info("start taking snapshot");
                // take snapshot
                String tmpSnapshotDir = snapshotManager.getSnapshotDir() + ".tmp";
                snapshotManager.updateMeta(tmpSnapshotDir, localLastAppliedIndex,
                        lastAppliedTerm, localConfiguration.build());
                String tmpSnapshotDataDir = tmpSnapshotDir + File.separator + "data";
                stateMachine.writeSnap(snapshotManager.getSnapshotDir(), tmpSnapshotDataDir, this, localLastAppliedIndex);
                // rename tmp snapshot dir to snapshot dir
                try {
                    File snapshotDirFile = new File(snapshotManager.getSnapshotDir());
                    if (snapshotDirFile.exists()) {
                        FileUtils.deleteDirectory(snapshotDirFile);
                    }
                    FileUtils.moveDirectory(new File(tmpSnapshotDir),
                            new File(snapshotManager.getSnapshotDir()));
                    LOG.info("end taking snapshot, result=success");
                    success = true;
                } catch (IOException ex) {
                    LOG.warn("move direct failed when taking snapshot, msg={}", ex.getMessage());
                }
            } finally {
                snapshotManager.getLock().unlock();
            }

            if (success) {
                // 重新加载snapshot
                long lastSnapshotIndex = 0;
                snapshotManager.getLock().lock();
                try {
                    snapshotManager.reload();
                    lastSnapshotIndex = snapshotManager.getMeta().getLastIncludedIndex();
                } finally {
                    snapshotManager.getLock().unlock();
                }

                // discard old log entries
                stateLock.lock();
                try {
                    if (lastSnapshotIndex > 0 && logManager.getFirstLogIndex() <= lastSnapshotIndex) {
                        logManager.truncatePrefix(lastSnapshotIndex + 1);
                    }
                } finally {
                    stateLock.unlock();
                }
            }
        } finally {
            snapshotManager.getIsTakeSnap().compareAndSet(true, false);
        }
    }

    // in lock
    public void applyConfig(RaftProto.LogEntry entry) {
        try {
            RaftProto.Configuration newConfiguration
                    = RaftProto.Configuration.parseFrom(entry.getData().toByteArray());
            clusterConfig = newConfiguration;
            // update peerMap
            for (RaftProto.Server server : newConfiguration.getServersList()) {
                if (!peerNodes.containsKey(server.getServerId())
                        && server.getServerId() != currentServer.getServerId()) {
                    ClusterNode peer = new ClusterNode(server);
                    peer.setNextIndex(logManager.getLastLogIndex() + 1);
                    peerNodes.put(server.getServerId(), peer);
                }
            }
            LOG.info("new conf is {}, leaderId={}", jsonFormat.printToString(newConfiguration), leaderId);
        } catch (InvalidProtocolBufferException ex) {
            ex.printStackTrace();
        }
    }

    public long getLastLogTerm() {
        long lastLogIndex = logManager.getLastLogIndex();
        if (lastLogIndex >= logManager.getFirstLogIndex()) {
            return logManager.getEntryTerm(lastLogIndex);
        } else {
            // log为空，lastLogIndex == lastSnapshotIndex
            return snapshotManager.getMeta().getLastIncludedTerm();
        }
    }

    /**
     * 选举定时器
     */
    private void resetElectionTimer() {
        if (electionTimer != null && !electionTimer.isDone()) {
            electionTimer.cancel(true);
        }
        electionTimer = timerExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                startPreVote();
            }
        }, getElectionTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    private int getElectionTimeoutMs() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int randomElectionTimeout = nodeOptions.getVoteTimeoutMs()
                + random.nextInt(0, nodeOptions.getVoteTimeoutMs());
        LOG.debug("new election time is after {} ms", randomElectionTimeout);
        return randomElectionTimeout;
    }

    /**
     * 客户端发起pre-vote请求。
     * pre-vote/vote是典型的二阶段实现。
     * 作用是防止某一个节点断网后，不断的增加term发起投票；
     * 当该节点网络恢复后，会导致集群其他节点的term增大，导致集群状态变更。
     */
    private void startPreVote() {
        stateLock.lock();
        try {
            if (!ConfigurationUtils.containsStorageServer(clusterConfig, currentServer.getServerId())) {
                resetElectionTimer();
                return;
            }
            LOG.info("Running pre-vote in term {}", currentTerm);
            nodeState = NodeState.STATE_PRE_CANDIDATE;
        } finally {
            stateLock.unlock();
        }

        for (RaftProto.Server server : clusterConfig.getServersList()) {
            if (server.getServerId() == currentServer.getServerId()) {
                continue;
            }
            final ClusterNode peer = peerNodes.get(server.getServerId());
            taskExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    preVote(peer);
                }
            });
        }
        resetElectionTimer();
    }

    /**
     * 客户端发起正式vote，对candidate有效
     */
    private void startVote() {
        stateLock.lock();
        try {
            if (!ConfigurationUtils.containsStorageServer(clusterConfig, currentServer.getServerId())) {
                resetElectionTimer();
                return;
            }
            currentTerm++;
            LOG.info("Running for election in term {}", currentTerm);
            nodeState = NodeState.STATE_CANDIDATE;
            leaderId = 0;
            votedFor = currentServer.getServerId();
        } finally {
            stateLock.unlock();
        }

        for (RaftProto.Server server : clusterConfig.getServersList()) {
            if (server.getServerId() == currentServer.getServerId()) {
                continue;
            }
            final ClusterNode peer = peerNodes.get(server.getServerId());
            taskExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    requestVote(peer);
                }
            });
        }
    }

    /**
     * 客户端发起pre-vote请求
     * @param peer 服务端节点信息
     */
    private void preVote(ClusterNode peer) {
        LOG.info("begin pre vote request");
        RaftProto.VoteRequest.Builder requestBuilder = RaftProto.VoteRequest.newBuilder();
        stateLock.lock();
        try {
            peer.setVoteGranted(null);
            requestBuilder.setServerId(currentServer.getServerId())
                    .setTerm(currentTerm)
                    .setLastLogIndex(logManager.getLastLogIndex())
                    .setLastLogTerm(getLastLogTerm());
        } finally {
            stateLock.unlock();
        }

        RaftProto.VoteRequest request = requestBuilder.build();
        // 暂时使用同步调用，后续需要找到正确的异步调用方式
        try {
            RaftProto.VoteResponse response = peer.getConsensusService().preElection(request);
            PreVoteResponseCallback callback = new PreVoteResponseCallback(peer, request);
            callback.success(response);
        } catch (Exception e) {
            PreVoteResponseCallback callback = new PreVoteResponseCallback(peer, request);
            callback.fail(e);
        }
    }

    /**
     * 客户端发起正式vote请求
     * @param peer 服务端节点信息
     */
    private void requestVote(ClusterNode peer) {
        LOG.info("begin vote request");
        RaftProto.VoteRequest.Builder requestBuilder = RaftProto.VoteRequest.newBuilder();
        stateLock.lock();
        try {
            peer.setVoteGranted(null);
            requestBuilder.setServerId(currentServer.getServerId())
                    .setTerm(currentTerm)
                    .setLastLogIndex(logManager.getLastLogIndex())
                    .setLastLogTerm(getLastLogTerm());
        } finally {
            stateLock.unlock();
        }

        RaftProto.VoteRequest request = requestBuilder.build();
        // 暂时使用同步调用，后续需要找到正确的异步调用方式
        try {
            RaftProto.VoteResponse response = peer.getConsensusService().requestElection(request);
            VoteResponseCallback callback = new VoteResponseCallback(peer, request);
            callback.success(response);
        } catch (Exception e) {
            VoteResponseCallback callback = new VoteResponseCallback(peer, request);
            callback.fail(e);
        }
    }

    private class PreVoteResponseCallback implements RpcCallback<RaftProto.VoteResponse> {
        private ClusterNode peer;
        private RaftProto.VoteRequest request;

        public PreVoteResponseCallback(ClusterNode peer, RaftProto.VoteRequest request) {
            this.peer = peer;
            this.request = request;
        }

        @Override
        public void success(RaftProto.VoteResponse response) {
            stateLock.lock();
            try {
                peer.setVoteGranted(response.getGranted());
                if (currentTerm != request.getTerm() || nodeState != NodeState.STATE_PRE_CANDIDATE) {
                    LOG.info("ignore preVote RPC result");
                    return;
                }
                if (response.getTerm() > currentTerm) {
                    LOG.info("Received pre vote response from server {} " +
                                    "in term {} (this server's term was {})",
                            peer.getStorageServer().getServerId(),
                            response.getTerm(),
                            currentTerm);
                    stepDown(response.getTerm());
                } else {
                    if (response.getGranted()) {
                        LOG.info("get pre vote granted from server {} for term {}",
                                peer.getStorageServer().getServerId(), currentTerm);
                        int voteGrantedNum = 1;
                        for (RaftProto.Server server : clusterConfig.getServersList()) {
                            if (server.getServerId() == currentServer.getServerId()) {
                                continue;
                            }
                            ClusterNode peer1 = peerNodes.get(server.getServerId());
                            if (peer1.isVoteGranted() != null && peer1.isVoteGranted() == true) {
                                voteGrantedNum += 1;
                            }
                        }
                        LOG.info("preVoteGrantedNum={}", voteGrantedNum);
                        if (voteGrantedNum > clusterConfig.getServersCount() / 2) {
                            LOG.info("get majority pre vote, serverId={} when pre vote, start vote",
                                    currentServer.getServerId());
                            startVote();
                        }
                    } else {
                        LOG.info("pre vote denied by server {} with term {}, my term is {}",
                                peer.getStorageServer().getServerId(), response.getTerm(), currentTerm);
                    }
                }
            } finally {
                stateLock.unlock();
            }
        }

        @Override
        public void fail(Throwable e) {
            LOG.warn("pre vote with peer[{}:{}] failed",
                    peer.getStorageServer().getEndpoint().getHost(),
                    peer.getStorageServer().getEndpoint().getPort());
            peer.setVoteGranted(new Boolean(false));
        }
    }

    private class VoteResponseCallback implements RpcCallback<RaftProto.VoteResponse> {
        private ClusterNode peer;
        private RaftProto.VoteRequest request;

        public VoteResponseCallback(ClusterNode peer, RaftProto.VoteRequest request) {
            this.peer = peer;
            this.request = request;
        }

        @Override
        public void success(RaftProto.VoteResponse response) {
            stateLock.lock();
            try {
                peer.setVoteGranted(response.getGranted());
                if (currentTerm != request.getTerm() || nodeState != NodeState.STATE_CANDIDATE) {
                    LOG.info("ignore requestVote RPC result");
                    return;
                }
                if (response.getTerm() > currentTerm) {
                    LOG.info("Received RequestVote response from server {} " +
                                    "in term {} (this server's term was {})",
                            peer.getStorageServer().getServerId(),
                            response.getTerm(),
                            currentTerm);
                    stepDown(response.getTerm());
                } else {
                    if (response.getGranted()) {
                        LOG.info("Got vote from server {} for term {}",
                                peer.getStorageServer().getServerId(), currentTerm);
                        int voteGrantedNum = 0;
                        if (votedFor == currentServer.getServerId()) {
                            voteGrantedNum += 1;
                        }
                        for (RaftProto.Server server : clusterConfig.getServersList()) {
                            if (server.getServerId() == currentServer.getServerId()) {
                                continue;
                            }
                            ClusterNode peer1 = peerNodes.get(server.getServerId());
                            if (peer1.isVoteGranted() != null && peer1.isVoteGranted() == true) {
                                voteGrantedNum += 1;
                            }
                        }
                        LOG.info("voteGrantedNum={}", voteGrantedNum);
                        if (voteGrantedNum > clusterConfig.getServersCount() / 2) {
                            LOG.info("Got majority vote, serverId={} become leader", currentServer.getServerId());
                            becomeLeader();
                        }
                    } else {
                        LOG.info("Vote denied by server {} with term {}, my term is {}",
                                peer.getStorageServer().getServerId(), response.getTerm(), currentTerm);
                    }
                }
            } finally {
                stateLock.unlock();
            }
        }

        @Override
        public void fail(Throwable e) {
            LOG.warn("requestVote with peer[{}:{}] failed",
                    peer.getStorageServer().getEndpoint().getHost(),
                    peer.getStorageServer().getEndpoint().getPort());
            peer.setVoteGranted(new Boolean(false));
        }
    }

    // in lock
    private void becomeLeader() {
        nodeState = NodeState.STATE_LEADER;
        leaderId = currentServer.getServerId();
        // stop vote timer
        if (electionTimer != null && !electionTimer.isDone()) {
            electionTimer.cancel(true);
        }
        // start heartbeat timer
        startNewHeartbeat();
    }

    // heartbeat timer, append entries
    // in lock
    private void resetHeartbeatTimer() {
        if (heartbeatTimer != null && !heartbeatTimer.isDone()) {
            heartbeatTimer.cancel(true);
        }
        heartbeatTimer = timerExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                startNewHeartbeat();
            }
        }, nodeOptions.getKeepAlivePeriodMs(), TimeUnit.MILLISECONDS);
    }

    // in lock, 开始心跳，对leader有效
    private void startNewHeartbeat() {
        LOG.debug("start new heartbeat, peers={}", peerNodes.keySet());
        for (final ClusterNode peer : peerNodes.values()) {
            taskExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    appendEntries(peer);
                }
            });
        }
        resetHeartbeatTimer();
    }

    // in lock, for leader
    private void advanceCommitIndex() {
        // 获取quorum matchIndex
        int peerNum = clusterConfig.getServersList().size();
        long[] matchIndexes = new long[peerNum];
        int i = 0;
        for (RaftProto.Server server : clusterConfig.getServersList()) {
            if (server.getServerId() != currentServer.getServerId()) {
                ClusterNode peer = peerNodes.get(server.getServerId());
                matchIndexes[i++] = peer.getMatchIndex();
            }
        }
        matchIndexes[i] = logManager.getLastLogIndex();
        Arrays.sort(matchIndexes);
        long newCommitIndex = matchIndexes[peerNum / 2];
        LOG.debug("newCommitIndex={}, oldCommitIndex={}", newCommitIndex, commitIndex);
        if (logManager.getEntryTerm(newCommitIndex) != currentTerm) {
            LOG.debug("newCommitIndexTerm={}, currentTerm={}",
                    logManager.getEntryTerm(newCommitIndex), currentTerm);
            return;
        }

        if (commitIndex >= newCommitIndex) {
            return;
        }
        long oldCommitIndex = commitIndex;
        commitIndex = newCommitIndex;
        logManager.updateMeta(currentTerm, null, logManager.getFirstLogIndex(), commitIndex);
        // 同步到状态机
        for (long index = oldCommitIndex + 1; index <= newCommitIndex; index++) {
            RaftProto.LogEntry entry = logManager.getEntry(index);
            if (entry.getType() == RaftProto.EntryType.ENTRY_TYPE_DATA) {
                stateMachine.applyData(entry.getData().toByteArray());
            } else if (entry.getType() == RaftProto.EntryType.ENTRY_TYPE_CONFIGURATION) {
                applyConfig(entry);
            }
        }
        lastAppliedIndex = commitIndex;
        LOG.debug("commitIndex={} lastAppliedIndex={}", commitIndex, lastAppliedIndex);
        commitCondition.signalAll();
    }

    // in lock
    private long packEntries(long nextIndex, RaftProto.AppendEntriesRequest.Builder requestBuilder) {
        long lastIndex = Math.min(logManager.getLastLogIndex(),
                nextIndex + nodeOptions.getMaxEntryBatchSize() - 1);
        for (long index = nextIndex; index <= lastIndex; index++) {
            RaftProto.LogEntry entry = logManager.getEntry(index);
            requestBuilder.addEntries(entry);
        }
        return lastIndex - nextIndex + 1;
    }

    private boolean installSnap(ClusterNode peer) {
        if (snapshotManager.getIsTakeSnap().get()) {
            LOG.info("already in take snapshot, please send install snapshot request later");
            return false;
        }
        if (!snapshotManager.getIsinstallSnap().compareAndSet(false, true)) {
            LOG.info("already in install snapshot");
            return false;
        }

        LOG.info("begin send install snapshot request to server={}", peer.getStorageServer().getServerId());
        boolean isSuccess = true;
        TreeMap<String, Snapshot.SnapshotDataFile> snapshotDataFileMap = snapshotManager.openSnapshotFiles();
        LOG.info("total snapshot files={}", snapshotDataFileMap.keySet());
        try {
            boolean isLastRequest = false;
            String lastFileName = null;
            long lastOffset = 0;
            long lastLength = 0;
            while (!isLastRequest) {
                RaftProto.InstallSnapshotRequest request
                        = buildInstallSnapshotRequest(snapshotDataFileMap, lastFileName, lastOffset, lastLength);
                if (request == null) {
                    LOG.warn("snapshot request == null");
                    isSuccess = false;
                    break;
                }
                if (request.getIsLast()) {
                    isLastRequest = true;
                }
                LOG.info("install snapshot request, fileName={}, offset={}, size={}, isFirst={}, isLast={}",
                        request.getFileName(), request.getOffset(), request.getData().toByteArray().length,
                        request.getIsFirst(), request.getIsLast());
                RaftProto.InstallSnapshotResponse response = null;
                // TODO: 需要实现正确的异步调用
                // response = peer.getRaftConsensusServiceAsync().deploySnapshot(request, callback);
                if (response != null && response.getResCode() == RaftProto.ResCode.RES_CODE_SUCCESS) {
                    lastFileName = request.getFileName();
                    lastOffset = request.getOffset();
                    lastLength = request.getData().size();
                } else {
                    isSuccess = false;
                    break;
                }
            }

            if (isSuccess) {
                long lastIncludedIndexInSnapshot;
                snapshotManager.getLock().lock();
                try {
                    lastIncludedIndexInSnapshot = snapshotManager.getMeta().getLastIncludedIndex();
                } finally {
                    snapshotManager.getLock().unlock();
                }

                stateLock.lock();
                try {
                    peer.setNextIndex(lastIncludedIndexInSnapshot + 1);
                } finally {
                    stateLock.unlock();
                }
            }
        } finally {
            snapshotManager.closeSnapshotFiles(snapshotDataFileMap);
            snapshotManager.getIsinstallSnap().compareAndSet(true, false);
        }
        LOG.info("end send install snapshot request to server={}, success={}",
                peer.getStorageServer().getServerId(), isSuccess);
        return isSuccess;
    }

    private RaftProto.InstallSnapshotRequest buildInstallSnapshotRequest(
            TreeMap<String, Snapshot.SnapshotDataFile> snapshotDataFileMap,
            String lastFileName, long lastOffset, long lastLength) {
        RaftProto.InstallSnapshotRequest.Builder requestBuilder = RaftProto.InstallSnapshotRequest.newBuilder();

        snapshotManager.getLock().lock();
        try {
            if (lastFileName == null) {
                lastFileName = snapshotDataFileMap.firstKey();
                lastOffset = 0;
                lastLength = 0;
            }
            Snapshot.SnapshotDataFile lastFile = snapshotDataFileMap.get(lastFileName);
            long lastFileLength = lastFile.randomAccessFile.length();
            String currentFileName = lastFileName;
            long currentOffset = lastOffset + lastLength;
            int currentDataSize = nodeOptions.getMaxSnapshotBytesPerRequest();
            Snapshot.SnapshotDataFile currentDataFile = lastFile;
            if (lastOffset + lastLength < lastFileLength) {
                if (lastOffset + lastLength + nodeOptions.getMaxSnapshotBytesPerRequest() > lastFileLength) {
                    currentDataSize = (int) (lastFileLength - (lastOffset + lastLength));
                }
            } else {
                Map.Entry<String, Snapshot.SnapshotDataFile> currentEntry
                        = snapshotDataFileMap.higherEntry(lastFileName);
                if (currentEntry == null) {
                    LOG.warn("reach the last file={}", lastFileName);
                    return null;
                }
                currentDataFile = currentEntry.getValue();
                currentFileName = currentEntry.getKey();
                currentOffset = 0;
                int currentFileLenght = (int) currentEntry.getValue().randomAccessFile.length();
                if (currentFileLenght < nodeOptions.getMaxSnapshotBytesPerRequest()) {
                    currentDataSize = currentFileLenght;
                }
            }
            byte[] currentData = new byte[currentDataSize];
            currentDataFile.randomAccessFile.seek(currentOffset);
            currentDataFile.randomAccessFile.read(currentData);
            requestBuilder.setData(ByteString.copyFrom(currentData));
            requestBuilder.setFileName(currentFileName);
            requestBuilder.setOffset(currentOffset);
            requestBuilder.setIsFirst(false);
            if (currentFileName.equals(snapshotDataFileMap.lastKey())
                    && currentOffset + currentDataSize >= currentDataFile.randomAccessFile.length()) {
                requestBuilder.setIsLast(true);
            } else {
                requestBuilder.setIsLast(false);
            }
            if (currentFileName.equals(snapshotDataFileMap.firstKey()) && currentOffset == 0) {
                requestBuilder.setIsFirst(true);
                requestBuilder.setSnapshotMetaData(snapshotManager.getMeta());
            } else {
                requestBuilder.setIsFirst(false);
            }
        } catch (Exception ex) {
            LOG.warn("meet exception:", ex);
            return null;
        } finally {
            snapshotManager.getLock().unlock();
        }

        stateLock.lock();
        try {
            requestBuilder.setTerm(currentTerm);
            requestBuilder.setServerId(currentServer.getServerId());
        } finally {
            stateLock.unlock();
        }

        return requestBuilder.build();
    }

    public boolean waitUntilApplied() {
        final CountDownLatch cdl;
        long readIndex;
        stateLock.lock();
        try {
            // 记录当前commitIndex为readIndex
            // 创建CountDownLatch，值为Peer节点数的一半（向上取整，加上Leader节点本身即可超过半数）
            readIndex = commitIndex;
            int peerNum = clusterConfig.getServersList().size();
            cdl = new CountDownLatch((peerNum + 1) >> 1);

            // 向所有Follower节点发送心跳包，如果得到响应就让CountDownLatch减一
            LOG.debug("ensure leader, peers={}", peerNodes.keySet());
            for (final ClusterNode peer : peerNodes.values()) {
                taskExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (appendEntries(peer)) {
                            cdl.countDown();
                        }
                    }
                });
            }
        } finally {
            stateLock.unlock();
        }

        // 等待CountDownLatch减为0或超时
        try {
            if (cdl.await(nodeOptions.getMaxAwaitTimeout(), TimeUnit.MILLISECONDS)) {
                stateLock.lock();
                try {
                    // 如果CountDownLatch在超时时间内减为0，则成功确认当前节点是Leader节点，等待readIndex之前的日志条目被应用到复制状态机
                    long startTime = System.currentTimeMillis();
                    while (lastAppliedIndex < readIndex
                            && System.currentTimeMillis() - startTime < nodeOptions.getMaxAwaitTimeout()) {
                        commitCondition.await(nodeOptions.getMaxAwaitTimeout(), TimeUnit.MILLISECONDS);
                    }
                    return lastAppliedIndex >= readIndex;
                } finally {
                    stateLock.unlock();
                }
            }
        } catch (InterruptedException ignore) {
        }
        return false;
    }

    public boolean waitForLeaderCommitIndex() {
        long readIndex = -1;
        boolean callLeader = false;
        ClusterNode leader = null;

        stateLock.lock();
        try {
            // 记录commitIndex为readIndex
            // 如果当前节点是Leader节点，则直接获取当前commitIndex，否则通过RPC从Leader节点获取commitIndex
            if (leaderId == currentServer.getServerId()) {
                readIndex = commitIndex;
            } else {
                callLeader = true;
                leader = peerNodes.get(leaderId);
            }
        } finally {
            stateLock.unlock();
        }

        if (callLeader && leader != null) {
            RaftProto.GetLeaderCommitIndexRequest request = RaftProto.GetLeaderCommitIndexRequest.newBuilder().build();
            RaftProto.GetLeaderCommitIndexResponse response = null;
            // TODO: 需要实现正确的调用
            // response = leader.getRaftConsensusServiceAsync().getLeaderCommitIndex(request);
        }

        if (readIndex == -1) {
            return false;
        }

        stateLock.lock();
        try {
            // 等待readIndex之前的日志条目被应用到复制状态机
            long startTime = System.currentTimeMillis();
            while (lastAppliedIndex < readIndex
                    && System.currentTimeMillis() - startTime < nodeOptions.getMaxAwaitTimeout()) {
                commitCondition.await(nodeOptions.getMaxAwaitTimeout(), TimeUnit.MILLISECONDS);
            }
            return lastAppliedIndex >= readIndex;
        } catch (InterruptedException ignore) {
        } finally {
            stateLock.unlock();
        }
        return false;
    }

    public Lock getLock() {
        return stateLock;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public int getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(int votedFor) {
        this.votedFor = votedFor;
    }

    public long getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(long commitIndex) {
        this.commitIndex = commitIndex;
    }

    public long getLastAppliedIndex() {
        return lastAppliedIndex;
    }

    public void setLastAppliedIndex(long lastAppliedIndex) {
        this.lastAppliedIndex = lastAppliedIndex;
    }

    public SegmentedLog getRaftLog() {
        return logManager;
    }

    public int getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(int leaderId) {
        this.leaderId = leaderId;
    }

    public Snapshot getSnapshot() {
        return snapshotManager;
    }

    public StateMachine getStateMachine() {
        return stateMachine;
    }

    public RaftProto.Configuration getConfig() {
        return clusterConfig;
    }

    public void setConfiguration(RaftProto.Configuration configuration) {
        this.clusterConfig = configuration;
    }

    public RaftProto.Server getLocalServer() {
        return currentServer;
    }

    public NodeState getState() {
        return nodeState;
    }

    public ConcurrentMap<Integer, ClusterNode> getPeerMap() {
        return peerNodes;
    }

    public ExecutorService getExecutorService() {
        return taskExecutor;
    }

    public Condition getCatchUpCondition() {
        return syncCondition;
    }

    public Condition getCommitIndexCondition() {
        return commitCondition;
    }
}
