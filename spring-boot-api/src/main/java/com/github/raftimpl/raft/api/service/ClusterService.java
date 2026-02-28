package com.github.raftimpl.raft.api.service;

import java.util.Map;

/**
 * 集群服务接口
 * 
 */
public interface ClusterService {

    /**
     * 获取集群信息
     * 
     * @return 集群信息
     */
    Map<String, Object> getClusterInfo();

    /**
     * 获取Leader节点信息
     * 
     * @return Leader节点信息
     */
    Map<String, Object> getLeaderInfo();

    /**
     * 获取所有节点信息
     * 
     * @return 所有节点信息
     */
    Map<String, Object> getNodesInfo();

    /**
     * 添加节点
     * 
     * @param nodeId 节点ID
     * @param host 主机
     * @param port 端口
     * @return 是否成功
     */
    boolean addNode(Integer nodeId, String host, Integer port);

    /**
     * 移除节点
     * 
     * @param nodeId 节点ID
     * @return 是否成功
     */
    boolean removeNode(Integer nodeId);

    /**
     * 获取集群状态
     * 
     * @return 集群状态
     */
    Map<String, Object> getClusterStatus();

    /**
     * 获取集群指标
     * 
     * @return 集群指标
     */
    Map<String, Object> getClusterMetrics();

    /**
     * 转移Leader
     * 
     * @param targetNodeId 目标节点ID
     * @return 是否成功
     */
    boolean transferLeader(Integer targetNodeId);

    /**
     * 触发快照
     * 
     * @return 是否成功
     */
    boolean triggerSnapshot();

    /**
     * 获取日志信息
     * 
     * @param startIndex 起始索引
     * @param endIndex 结束索引
     * @return 日志信息
     */
    Map<String, Object> getLogInfo(long startIndex, long endIndex);
} 