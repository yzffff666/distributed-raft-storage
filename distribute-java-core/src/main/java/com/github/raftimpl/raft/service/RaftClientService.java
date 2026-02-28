package com.github.raftimpl.raft.service;

import com.github.raftimpl.raft.proto.RaftProto;

/**
 * Raft客户端服务接口
 * 为外部客户端提供集群管理功能，包括获取集群信息、动态配置节点等操作
 * 
 */
public interface RaftClientService {

    /**
     * 获取raft集群leader节点信息
     * 客户端可以通过此方法找到当前的leader节点进行读写操作
     * 
     * @param request 获取leader请求
     * @return leader节点信息，包含节点ID和地址
     */
    RaftProto.GetLeaderResponse getNowLeader(RaftProto.GetLeaderRequest request);

    /**
     * 获取raft集群所有节点信息
     * 返回集群中所有节点的配置信息和状态
     * 
     * @param request 获取配置请求
     * @return raft集群各节点地址，以及主从关系
     */
    RaftProto.GetConfigurationResponse getConfig(RaftProto.GetConfigurationRequest request);

    /**
     * 向raft集群添加节点
     * 动态扩容集群，添加新的节点到Raft集群中
     * 
     * @param request 要添加的节点信息
     * @return 添加操作的结果，包含成功与否的状态码
     */
    RaftProto.AddPeersResponse addStoragePeers(RaftProto.AddPeersRequest request);

    /**
     * 从raft集群删除节点
     * 动态缩容集群，从Raft集群中移除指定节点
     * 
     * @param request 要删除的节点信息
     * @return 删除操作的结果，包含成功与否的状态码
     */
    RaftProto.RemovePeersResponse removeStoragePeers(RaftProto.RemovePeersRequest request);
}
