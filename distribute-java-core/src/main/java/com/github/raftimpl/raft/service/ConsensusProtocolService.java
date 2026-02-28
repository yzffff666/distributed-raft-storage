package com.github.raftimpl.raft.service;

import com.github.raftimpl.raft.proto.RaftProto;

/**
 * 共识协议服务接口
 * 定义了Raft共识算法的核心RPC方法，包括预投票、投票、日志复制等
 * 
 */
public interface ConsensusProtocolService {

    /**
     * 预投票RPC
     * 在正式投票前进行预投票，用于减少不必要的选举轮次
     * 
     * @param request 预投票请求
     * @return 预投票响应
     */
    RaftProto.VoteResponse preElection(RaftProto.VoteRequest request);

    /**
     * 请求投票RPC
     * Candidate节点在选举过程中向其他节点请求投票
     * 
     * @param request 投票请求
     * @return 投票响应
     */
    RaftProto.VoteResponse requestElection(RaftProto.VoteRequest request);

    /**
     * 追加日志条目RPC
     * Leader节点用于向Follower节点复制日志条目，也用作心跳
     * 
     * @param request 追加日志请求
     * @return 追加日志响应
     */
    RaftProto.AppendEntriesResponse replicateEntries(RaftProto.AppendEntriesRequest request);

    /**
     * 安装快照RPC
     * Leader节点向落后太多的Follower节点发送快照数据
     * 
     * @param request 安装快照请求
     * @return 安装快照响应
     */
    RaftProto.InstallSnapshotResponse deploySnapshot(RaftProto.InstallSnapshotRequest request);
    
    /**
     * 获取Leader提交索引RPC
     * 用于查询Leader节点当前的提交索引
     * 
     * @param request 获取提交索引请求
     * @return 获取提交索引响应
     */
    RaftProto.GetLeaderCommitIndexResponse getLeaderCommitIndex(RaftProto.GetLeaderCommitIndexRequest request);
}
