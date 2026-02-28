package com.github.raftimpl.raft.service;

import com.baidu.brpc.client.RpcCallback;
import com.github.raftimpl.raft.proto.RaftProto;

import java.util.concurrent.Future;

/**
 * 异步共识协议服务接口
 * 提供Raft共识算法的异步RPC方法，支持回调机制
 * 用于节点间的异步通信，提高系统性能
 * 
 */
public interface ConsensusProtocolServiceAsync {

    /**
     * 同步预投票RPC
     * 在正式投票前进行预投票，减少不必要的选举轮次
     * 
     * @param request 预投票请求
     * @return 预投票响应
     */
    RaftProto.VoteResponse preElection(RaftProto.VoteRequest request);

    /**
     * 异步预投票RPC
     * 在正式投票前进行预投票，减少不必要的选举轮次
     * 
     * @param request 预投票请求
     * @param callback 异步回调处理器
     * @return Future对象，可用于获取异步结果
     */
    Future<RaftProto.VoteResponse> preElection(
            RaftProto.VoteRequest request,
            RpcCallback<RaftProto.VoteResponse> callback);

    /**
     * 同步请求投票RPC
     * Candidate节点在选举过程中向其他节点请求投票
     * 
     * @param request 投票请求
     * @return 投票响应
     */
    RaftProto.VoteResponse requestElection(RaftProto.VoteRequest request);

    /**
     * 异步请求投票RPC
     * Candidate节点在选举过程中向其他节点异步请求投票
     * 
     * @param request 投票请求
     * @param callback 异步回调处理器
     * @return Future对象，可用于获取异步结果
     */
    Future<RaftProto.VoteResponse> requestElection(
            RaftProto.VoteRequest request,
            RpcCallback<RaftProto.VoteResponse> callback);

    /**
     * 同步追加日志条目RPC
     * Leader节点向Follower节点复制日志条目，也用作心跳
     * 
     * @param request 追加日志请求
     * @return 追加日志响应
     */
    RaftProto.AppendEntriesResponse replicateEntries(RaftProto.AppendEntriesRequest request);

    /**
     * 异步追加日志条目RPC
     * Leader节点异步向Follower节点复制日志条目，也用作心跳
     * 
     * @param request 追加日志请求
     * @param callback 异步回调处理器
     * @return Future对象，可用于获取异步结果
     */
    Future<RaftProto.AppendEntriesResponse> replicateEntries(
            RaftProto.AppendEntriesRequest request,
            RpcCallback<RaftProto.AppendEntriesResponse> callback);

    /**
     * 同步安装快照RPC
     * Leader节点向落后太多的Follower节点发送快照数据
     * 
     * @param request 安装快照请求
     * @return 安装快照响应
     */
    RaftProto.InstallSnapshotResponse deploySnapshot(RaftProto.InstallSnapshotRequest request);

    /**
     * 异步安装快照RPC
     * Leader节点异步向落后太多的Follower节点发送快照数据
     * 
     * @param request 安装快照请求
     * @param callback 异步回调处理器
     * @return Future对象，可用于获取异步结果
     */
    Future<RaftProto.InstallSnapshotResponse> deploySnapshot(
            RaftProto.InstallSnapshotRequest request,
            RpcCallback<RaftProto.InstallSnapshotResponse> callback);

    /**
     * 获取Leader提交索引RPC
     * 用于查询Leader节点当前的提交索引
     * 
     * @param request 获取提交索引请求
     * @return 获取提交索引响应
     */
    RaftProto.GetLeaderCommitIndexResponse getLeaderCommitIndex(RaftProto.GetLeaderCommitIndexRequest request);
}
