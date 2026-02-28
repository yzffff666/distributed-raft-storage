package com.github.raftimpl.raft;

import com.baidu.brpc.client.BrpcProxy;
import com.baidu.brpc.client.RpcClient;
import com.baidu.brpc.client.instance.Endpoint;
import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.service.ConsensusProtocolService;
import com.github.raftimpl.raft.service.ConsensusProtocolServiceAsync;

/**
 * ClusterNode类代表Raft集群中的一个其他节点
 * 主要用于存储节点信息、管理RPC连接和维护日志复制状态
 * 
 */
public class ClusterNode {
    /** 节点服务器信息，包含节点ID、主机和端口等 */
    private RaftProto.Server nodeInfo;
    
    /** RPC客户端，用于连接到此节点 */
    private RpcClient rpcClient;
    
    /** Raft共识服务代理，用于发送日志同步、选举等请求 */
    private ConsensusProtocolService raftConsensusService;
    
    /** 需要发送给follower的下一个日志条目的索引值，只对leader有效 */
    private long nextLogIndex;
    
    /** 已复制日志的最高索引值，用于跟踪日志复制进度 */
    private long confirmedIndex;
    
    /** 投票结果，记录该节点是否给当前节点投票 */
    private volatile Boolean hasVoted;
    
    /** 标识该节点是否已经追上leader的日志进度 */
    private volatile boolean isCatchUp;

    /**
     * 构造函数，创建ClusterNode实例
     * 
     * @param nodeInfo 节点服务器信息
     */
    public ClusterNode(RaftProto.Server nodeInfo) {
        this.nodeInfo = nodeInfo;
        // 创建同步RPC客户端
        this.rpcClient = new RpcClient(new Endpoint(
                nodeInfo.getEndpoint().getHost(),
                nodeInfo.getEndpoint().getPort()));
        // 创建同步服务代理
        raftConsensusService = BrpcProxy.getProxy(rpcClient, ConsensusProtocolService.class);
        isCatchUp = false;
    }

    /**
     * 获取服务器信息
     * 
     * @return 服务器配置信息
     */
    public RaftProto.Server getStorageServer() {
        return nodeInfo;
    }

    /**
     * 获取同步RPC客户端
     * 
     * @return 同步RPC客户端实例
     */
    public RpcClient getRpcClient() {
        return rpcClient;
    }

    /**
     * 获取同步Raft共识服务
     * 
     * @return 同步服务代理
     */
    public ConsensusProtocolService getConsensusService() {
        return raftConsensusService;
    }

    /**
     * 获取下一个需要发送的日志索引
     * 
     * @return 下一个日志索引
     */
    public long getNextIndex() {
        return nextLogIndex;
    }

    /**
     * 设置下一个需要发送的日志索引
     * 
     * @param nextIndex 下一个日志索引
     */
    public void setNextIndex(long nextIndex) {
        this.nextLogIndex = nextIndex;
    }

    /**
     * 获取已匹配的日志索引
     * 
     * @return 已匹配的日志索引
     */
    public long getMatchIndex() {
        return confirmedIndex;
    }

    /**
     * 设置已匹配的日志索引
     * 
     * @param matchIndex 已匹配的日志索引
     */
    public void setMatchIndex(long matchIndex) {
        this.confirmedIndex = matchIndex;
    }

    /**
     * 获取投票结果
     * 
     * @return 是否投票给当前节点
     */
    public Boolean isVoteGranted() {
        return hasVoted;
    }

    /**
     * 设置投票结果
     * 
     * @param voteGranted 投票结果
     */
    public void setVoteGranted(Boolean voteGranted) {
        this.hasVoted = voteGranted;
    }

    /**
     * 检查节点是否已追上进度
     * 
     * @return 是否已追上进度
     */
    public boolean isCatchUp() {
        return isCatchUp;
    }

    /**
     * 设置节点追赶状态
     * 
     * @param catchUp 是否已追上进度
     */
    public void setCatchUp(boolean catchUp) {
        isCatchUp = catchUp;
    }
}
