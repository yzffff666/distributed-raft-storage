package com.github.raftimpl.raft.api.dto;

/**
 * 节点信息DTO
 * 用于表示Raft集群中节点的基本信息
 * 
 */
public class NodeInfo {
    /** 节点ID */
    private String nodeId;
    
    /** 节点主机地址 */
    private String host;
    
    /** 节点端口 */
    private int port;
    
    /** 节点角色 */
    private NodeRole role;
    
    /** 节点状态 */
    private NodeStatus status;
    
    /** 节点权重（用于加权负载均衡） */
    private int weight;
    
    /** 节点地理位置/区域 */
    private String region;
    
    /** 最后心跳时间 */
    private long lastHeartbeat;

    // 默认构造函数
    public NodeInfo() {}

    // 构造函数
    public NodeInfo(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.weight = 1;
        this.status = NodeStatus.UNKNOWN;
        this.role = NodeRole.FOLLOWER;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public NodeRole getRole() { return role; }
    public void setRole(NodeRole role) { this.role = role; }

    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    /**
     * 获取节点的完整地址
     * 
     * @return 节点地址
     */
    public String getAddress() {
        return host + ":" + port;
    }

    /**
     * 检查节点是否为Leader
     * 
     * @return 是否为Leader
     */
    public boolean isLeader() {
        return role == NodeRole.LEADER;
    }

    /**
     * 检查节点是否可用
     * 
     * @return 节点是否可用
     */
    public boolean isAvailable() {
        return status == NodeStatus.HEALTHY;
    }

    /**
     * 节点角色枚举
     */
    public enum NodeRole {
        LEADER,
        FOLLOWER,
        CANDIDATE
    }

    /**
     * 节点状态枚举
     */
    public enum NodeStatus {
        HEALTHY,
        UNHEALTHY,
        UNKNOWN,
        DISCONNECTED
    }

    @Override
    public String toString() {
        return "NodeInfo{" +
                "nodeId='" + nodeId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", role=" + role +
                ", status=" + status +
                ", weight=" + weight +
                ", region='" + region + '\'' +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return nodeId != null ? nodeId.equals(nodeInfo.nodeId) : nodeInfo.nodeId == null;
    }

    @Override
    public int hashCode() {
        return nodeId != null ? nodeId.hashCode() : 0;
    }
} 