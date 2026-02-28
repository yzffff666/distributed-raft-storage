package com.github.raftimpl.raft.api.service.impl;

import com.github.raftimpl.raft.api.dto.NodeInfo;
import com.github.raftimpl.raft.api.service.LoadBalancerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 负载均衡服务实现
 * 提供多种负载均衡策略和读写分离功能
 * 
 */
@Service
public class LoadBalancerServiceImpl implements LoadBalancerService {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerServiceImpl.class);
    
    /** 节点注册表 */
    private final Map<String, NodeInfo> nodeRegistry = new ConcurrentHashMap<>();
    
    /** 节点健康状态表 */
    private final Map<String, Boolean> nodeHealthStatus = new ConcurrentHashMap<>();
    
    /** 节点负载信息表 */
    private final Map<String, NodeLoadInfo> nodeLoadInfo = new ConcurrentHashMap<>();
    
    /** 轮询计数器 */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    /** 加权轮询状态 */
    private final Map<String, Integer> weightedRoundRobinState = new ConcurrentHashMap<>();
    
    /** 一致性哈希环 */
    private final TreeMap<Integer, String> consistentHashRing = new TreeMap<>();
    
    /** 虚拟节点数量 */
    private static final int VIRTUAL_NODES = 150;

    @Override
    public NodeInfo getLeaderNode() {
        logger.debug("查找Leader节点");
        
        return nodeRegistry.values().stream()
                .filter(node -> node.isLeader() && node.isAvailable())
                .findFirst()
                .orElse(null);
    }

    @Override
    public NodeInfo getReadNode(LoadBalanceStrategy strategy) {
        List<NodeInfo> availableNodes = getAvailableNodes();
        
        if (availableNodes.isEmpty()) {
            logger.warn("没有可用的节点处理读请求");
            return null;
        }
        
        logger.debug("使用{}策略选择读节点，可用节点数: {}", strategy, availableNodes.size());
        
        switch (strategy) {
            case ROUND_ROBIN:
                return selectByRoundRobin(availableNodes);
            case RANDOM:
                return selectByRandom(availableNodes);
            case LEAST_CONNECTIONS:
                return selectByLeastConnections(availableNodes);
            case WEIGHTED_ROUND_ROBIN:
                return selectByWeightedRoundRobin(availableNodes);
            case CONSISTENT_HASH:
                return selectByConsistentHash(availableNodes);
            case LOCALITY_AWARE:
                return selectByLocality(availableNodes);
            default:
                return selectByRoundRobin(availableNodes);
        }
    }

    @Override
    public List<NodeInfo> getAvailableNodes() {
        return nodeRegistry.values().stream()
                .filter(node -> isNodeHealthy(node.getNodeId()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isNodeHealthy(String nodeId) {
        Boolean healthy = nodeHealthStatus.get(nodeId);
        if (healthy == null) {
            // 如果没有健康状态记录，检查最后心跳时间
            NodeInfo node = nodeRegistry.get(nodeId);
            if (node != null) {
                long timeSinceLastHeartbeat = System.currentTimeMillis() - node.getLastHeartbeat();
                return timeSinceLastHeartbeat < 30000; // 30秒内有心跳认为健康
            }
            return false;
        }
        return healthy;
    }

    @Override
    public void updateNodeHealth(String nodeId, boolean healthy) {
        logger.debug("更新节点{}健康状态: {}", nodeId, healthy);
        nodeHealthStatus.put(nodeId, healthy);
        
        // 更新节点心跳时间
        NodeInfo node = nodeRegistry.get(nodeId);
        if (node != null && healthy) {
            node.setLastHeartbeat(System.currentTimeMillis());
        }
    }

    @Override
    public NodeLoadInfo getNodeLoad(String nodeId) {
        return nodeLoadInfo.computeIfAbsent(nodeId, NodeLoadInfo::new);
    }

    @Override
    public void registerNode(NodeInfo nodeInfo) {
        logger.info("注册节点: {}", nodeInfo);
        nodeRegistry.put(nodeInfo.getNodeId(), nodeInfo);
        nodeHealthStatus.put(nodeInfo.getNodeId(), true);
        nodeLoadInfo.put(nodeInfo.getNodeId(), new NodeLoadInfo(nodeInfo.getNodeId()));
        
        // 添加到一致性哈希环
        addToConsistentHashRing(nodeInfo.getNodeId());
    }

    @Override
    public void unregisterNode(String nodeId) {
        logger.info("注销节点: {}", nodeId);
        nodeRegistry.remove(nodeId);
        nodeHealthStatus.remove(nodeId);
        nodeLoadInfo.remove(nodeId);
        weightedRoundRobinState.remove(nodeId);
        
        // 从一致性哈希环移除
        removeFromConsistentHashRing(nodeId);
    }

    /**
     * 轮询策略选择节点
     */
    private NodeInfo selectByRoundRobin(List<NodeInfo> availableNodes) {
        int index = roundRobinCounter.getAndIncrement() % availableNodes.size();
        return availableNodes.get(index);
    }

    /**
     * 随机策略选择节点
     */
    private NodeInfo selectByRandom(List<NodeInfo> availableNodes) {
        int index = ThreadLocalRandom.current().nextInt(availableNodes.size());
        return availableNodes.get(index);
    }

    /**
     * 最少连接策略选择节点
     */
    private NodeInfo selectByLeastConnections(List<NodeInfo> availableNodes) {
        return availableNodes.stream()
                .min(Comparator.comparingInt(node -> 
                    getNodeLoad(node.getNodeId()).getActiveConnections()))
                .orElse(availableNodes.get(0));
    }

    /**
     * 加权轮询策略选择节点
     */
    private NodeInfo selectByWeightedRoundRobin(List<NodeInfo> availableNodes) {
        // 计算总权重
        int totalWeight = availableNodes.stream()
                .mapToInt(NodeInfo::getWeight)
                .sum();
        
        if (totalWeight <= 0) {
            return selectByRoundRobin(availableNodes);
        }
        
        // 生成随机数
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        
        // 选择节点
        int currentWeight = 0;
        for (NodeInfo node : availableNodes) {
            currentWeight += node.getWeight();
            if (randomWeight < currentWeight) {
                return node;
            }
        }
        
        return availableNodes.get(0);
    }

    /**
     * 一致性哈希策略选择节点
     */
    private NodeInfo selectByConsistentHash(List<NodeInfo> availableNodes) {
        if (consistentHashRing.isEmpty()) {
            return selectByRoundRobin(availableNodes);
        }
        
        // 使用当前时间作为哈希键（实际应用中可以使用请求的某个特征）
        int hash = String.valueOf(System.currentTimeMillis()).hashCode();
        
        // 查找顺时针方向最近的节点
        Map.Entry<Integer, String> entry = consistentHashRing.ceilingEntry(hash);
        if (entry == null) {
            entry = consistentHashRing.firstEntry();
        }
        
        String nodeId = entry.getValue();
        return nodeRegistry.get(nodeId);
    }

    /**
     * 就近访问策略选择节点
     */
    private NodeInfo selectByLocality(List<NodeInfo> availableNodes) {
        // 简化实现：优先选择本地区域的节点
        String currentRegion = getCurrentRegion();
        
        // 首先尝试选择同区域的节点
        List<NodeInfo> localNodes = availableNodes.stream()
                .filter(node -> currentRegion.equals(node.getRegion()))
                .collect(Collectors.toList());
        
        if (!localNodes.isEmpty()) {
            return selectByLeastConnections(localNodes);
        }
        
        // 如果没有同区域节点，选择负载最低的节点
        return selectByLeastConnections(availableNodes);
    }

    /**
     * 添加节点到一致性哈希环
     */
    private void addToConsistentHashRing(String nodeId) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            String virtualNodeId = nodeId + "#" + i;
            int hash = virtualNodeId.hashCode();
            consistentHashRing.put(hash, nodeId);
        }
    }

    /**
     * 从一致性哈希环移除节点
     */
    private void removeFromConsistentHashRing(String nodeId) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            String virtualNodeId = nodeId + "#" + i;
            int hash = virtualNodeId.hashCode();
            consistentHashRing.remove(hash);
        }
    }

    /**
     * 获取当前区域（简化实现）
     */
    private String getCurrentRegion() {
        // 实际实现中可以从配置文件或环境变量获取
        return System.getProperty("raft.node.region", "default");
    }

    /**
     * 更新节点负载信息
     * 
     * @param nodeId 节点ID
     * @param cpuUsage CPU使用率
     * @param memoryUsage 内存使用率
     * @param activeConnections 活跃连接数
     * @param responseTime 响应时间
     * @param qps QPS
     */
    public void updateNodeLoadInfo(String nodeId, double cpuUsage, double memoryUsage,
                                   int activeConnections, long responseTime, int qps) {
        NodeLoadInfo loadInfo = getNodeLoad(nodeId);
        loadInfo.setCpuUsage(cpuUsage);
        loadInfo.setMemoryUsage(memoryUsage);
        loadInfo.setActiveConnections(activeConnections);
        loadInfo.setResponseTime(responseTime);
        loadInfo.setQps(qps);
        
        logger.debug("更新节点{}负载信息: CPU={}%, 内存={}%, 连接数={}, 响应时间={}ms, QPS={}", 
                nodeId, cpuUsage, memoryUsage, activeConnections, responseTime, qps);
    }

    /**
     * 获取负载均衡统计信息
     */
    public Map<String, Object> getLoadBalancerStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", nodeRegistry.size());
        stats.put("healthyNodes", getAvailableNodes().size());
        stats.put("leaderNode", getLeaderNode());
        stats.put("roundRobinCounter", roundRobinCounter.get());
        
        // 节点负载统计
        Map<String, Double> nodeLoads = new HashMap<>();
        nodeLoadInfo.forEach((nodeId, loadInfo) -> 
            nodeLoads.put(nodeId, loadInfo.getLoadScore()));
        stats.put("nodeLoads", nodeLoads);
        
        return stats;
    }
} 