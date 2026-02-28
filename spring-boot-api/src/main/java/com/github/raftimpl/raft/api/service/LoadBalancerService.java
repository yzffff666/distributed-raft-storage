package com.github.raftimpl.raft.api.service;

import com.github.raftimpl.raft.api.dto.NodeInfo;
import java.util.List;

/**
 * 负载均衡服务接口
 * 提供读写分离和负载均衡功能，优化系统性能
 * 
 */
public interface LoadBalancerService {

    /**
     * 获取写操作的Leader节点
     * 所有写操作都路由到Leader节点
     * 
     * @return Leader节点信息
     */
    NodeInfo getLeaderNode();

    /**
     * 获取读操作的节点（支持读写分离）
     * 根据负载均衡策略选择合适的节点处理读请求
     * 
     * @param strategy 负载均衡策略
     * @return 选中的节点信息
     */
    NodeInfo getReadNode(LoadBalanceStrategy strategy);

    /**
     * 获取所有可用的节点列表
     * 
     * @return 可用节点列表
     */
    List<NodeInfo> getAvailableNodes();

    /**
     * 检查节点健康状态
     * 
     * @param nodeId 节点ID
     * @return 节点是否健康
     */
    boolean isNodeHealthy(String nodeId);

    /**
     * 更新节点健康状态
     * 
     * @param nodeId 节点ID
     * @param healthy 健康状态
     */
    void updateNodeHealth(String nodeId, boolean healthy);

    /**
     * 获取节点负载信息
     * 
     * @param nodeId 节点ID
     * @return 节点负载信息
     */
    NodeLoadInfo getNodeLoad(String nodeId);

    /**
     * 注册节点
     * 
     * @param nodeInfo 节点信息
     */
    void registerNode(NodeInfo nodeInfo);

    /**
     * 注销节点
     * 
     * @param nodeId 节点ID
     */
    void unregisterNode(String nodeId);

    /**
     * 负载均衡策略枚举
     */
    enum LoadBalanceStrategy {
        /** 轮询策略 */
        ROUND_ROBIN,
        /** 随机策略 */
        RANDOM,
        /** 最少连接策略 */
        LEAST_CONNECTIONS,
        /** 加权轮询策略 */
        WEIGHTED_ROUND_ROBIN,
        /** 一致性哈希策略 */
        CONSISTENT_HASH,
        /** 就近访问策略 */
        LOCALITY_AWARE
    }

    /**
     * 节点负载信息
     */
    class NodeLoadInfo {
        private String nodeId;
        private int activeConnections;
        private double cpuUsage;
        private double memoryUsage;
        private long responseTime;
        private int qps;

        // 构造函数
        public NodeLoadInfo(String nodeId) {
            this.nodeId = nodeId;
        }

        // Getters and Setters
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        
        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
        
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
        
        public double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
        
        public long getResponseTime() { return responseTime; }
        public void setResponseTime(long responseTime) { this.responseTime = responseTime; }
        
        public int getQps() { return qps; }
        public void setQps(int qps) { this.qps = qps; }
        
        /**
         * 计算节点负载分数（越小越好）
         * 
         * @return 负载分数
         */
        public double getLoadScore() {
            return (cpuUsage * 0.3) + (memoryUsage * 0.3) + 
                   (activeConnections * 0.2) + (responseTime / 1000.0 * 0.2);
        }
    }
} 