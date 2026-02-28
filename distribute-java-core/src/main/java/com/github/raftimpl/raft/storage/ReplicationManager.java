package com.github.raftimpl.raft.storage;

import com.github.raftimpl.raft.ClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 副本管理器
 * 负责数据副本的创建、维护、故障恢复和一致性保证
 * 
 */
public class ReplicationManager {

    private static final Logger LOG = LoggerFactory.getLogger(ReplicationManager.class);

    private ShardingManager shardingManager;

    // 默认副本数量
    private int replicationFactor = 3;

    // 副本同步超时时间（毫秒）
    private long syncTimeout = 5000;

    // 副本健康检查间隔（秒）
    private int healthCheckInterval = 30;

    // 副本状态信息
    private final Map<String, ReplicationInfo> replicationInfoMap = new ConcurrentHashMap<>();
    
    // 副本同步任务队列
    private final Map<String, ReplicationTask> replicationTasks = new ConcurrentHashMap<>();
    
    // 读写锁保护副本操作
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * 构造函数
     */
    public ReplicationManager(ShardingManager shardingManager) {
        this.shardingManager = shardingManager;
    }

    /**
     * 副本信息
     */
    public static class ReplicationInfo {
        private final String objectKey;
        private final ClusterNode primaryNode;
        private final Set<ClusterNode> replicaNodes;
        private final Map<ClusterNode, ReplicaStatus> replicaStatusMap;
        private final long createTime;
        private long lastSyncTime;
        private String status; // HEALTHY, DEGRADED, FAILED
        
        public ReplicationInfo(String objectKey, ClusterNode primaryNode) {
            this.objectKey = objectKey;
            this.primaryNode = primaryNode;
            this.replicaNodes = new HashSet<>();
            this.replicaStatusMap = new ConcurrentHashMap<>();
            this.createTime = System.currentTimeMillis();
            this.lastSyncTime = System.currentTimeMillis();
            this.status = "HEALTHY";
        }
        
        // Getters and Setters
        public String getObjectKey() { return objectKey; }
        public ClusterNode getPrimaryNode() { return primaryNode; }
        public Set<ClusterNode> getReplicaNodes() { return replicaNodes; }
        public Map<ClusterNode, ReplicaStatus> getReplicaStatusMap() { return replicaStatusMap; }
        public long getCreateTime() { return createTime; }
        public long getLastSyncTime() { return lastSyncTime; }
        public void setLastSyncTime(long lastSyncTime) { this.lastSyncTime = lastSyncTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * 副本状态
     */
    public static class ReplicaStatus {
        private final ClusterNode node;
        private String status; // SYNCED, SYNCING, FAILED, STALE
        private long lastSyncTime;
        private long version;
        private String checksum;
        
        public ReplicaStatus(ClusterNode node) {
            this.node = node;
            this.status = "SYNCING";
            this.lastSyncTime = System.currentTimeMillis();
            this.version = 0;
        }
        
        // Getters and Setters
        public ClusterNode getNode() { return node; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getLastSyncTime() { return lastSyncTime; }
        public void setLastSyncTime(long lastSyncTime) { this.lastSyncTime = lastSyncTime; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
    }

    /**
     * 副本同步任务
     */
    public static class ReplicationTask {
        private final String objectKey;
        private final ClusterNode sourceNode;
        private final ClusterNode targetNode;
        private final String operation; // CREATE, UPDATE, DELETE
        private final long createTime;
        private String status; // PENDING, RUNNING, COMPLETED, FAILED
        private int retryCount;
        
        public ReplicationTask(String objectKey, ClusterNode sourceNode, ClusterNode targetNode, String operation) {
            this.objectKey = objectKey;
            this.sourceNode = sourceNode;
            this.targetNode = targetNode;
            this.operation = operation;
            this.createTime = System.currentTimeMillis();
            this.status = "PENDING";
            this.retryCount = 0;
        }
        
        // Getters and Setters
        public String getObjectKey() { return objectKey; }
        public ClusterNode getSourceNode() { return sourceNode; }
        public ClusterNode getTargetNode() { return targetNode; }
        public String getOperation() { return operation; }
        public long getCreateTime() { return createTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { this.retryCount++; }
    }

    /**
     * 初始化副本管理器
     */
    public void initialize() {
        // 启动副本健康检查任务
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 
                healthCheckInterval, healthCheckInterval, TimeUnit.SECONDS);
        
        // 启动副本同步任务处理器
        scheduler.scheduleAtFixedRate(this::processReplicationTasks, 
                5, 5, TimeUnit.SECONDS);
        
        LOG.info("副本管理器初始化完成，副本因子: {}, 同步超时: {}ms, 健康检查间隔: {}s", 
                replicationFactor, syncTimeout, healthCheckInterval);
    }

    /**
     * 创建对象副本
     */
    public ReplicationInfo createReplicas(String objectKey, byte[] data, String checksum) {
        lock.writeLock().lock();
        try {
            // 获取主节点
            ClusterNode primaryNode = shardingManager.getNodeForKey(objectKey);
            if (primaryNode == null) {
                throw new IllegalStateException("没有可用的主节点");
            }
            
            ReplicationInfo replicationInfo = new ReplicationInfo(objectKey, primaryNode);
            
            // 选择副本节点
            Set<ClusterNode> replicaNodes = selectReplicaNodes(primaryNode, replicationFactor - 1);
            replicationInfo.getReplicaNodes().addAll(replicaNodes);
            
            // 初始化副本状态
            for (ClusterNode replicaNode : replicaNodes) {
                ReplicaStatus status = new ReplicaStatus(replicaNode);
                status.setChecksum(checksum);
                replicationInfo.getReplicaStatusMap().put(replicaNode, status);
                
                // 创建副本同步任务
                ReplicationTask task = new ReplicationTask(objectKey, primaryNode, replicaNode, "CREATE");
                replicationTasks.put(generateTaskId(objectKey, replicaNode), task);
            }
            
            replicationInfoMap.put(objectKey, replicationInfo);
            
            LOG.info("创建对象副本: key={}, 主节点={}, 副本节点数={}", 
                    objectKey, primaryNode.getStorageServer().getServerId(), replicaNodes.size());
            
            return replicationInfo;
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 更新对象副本
     */
    public boolean updateReplicas(String objectKey, byte[] data, String checksum) {
        lock.readLock().lock();
        try {
            ReplicationInfo replicationInfo = replicationInfoMap.get(objectKey);
            if (replicationInfo == null) {
                LOG.warn("对象副本信息不存在: {}", objectKey);
                return false;
            }
            
            // 更新所有副本节点
            for (ClusterNode replicaNode : replicationInfo.getReplicaNodes()) {
                ReplicaStatus status = replicationInfo.getReplicaStatusMap().get(replicaNode);
                if (status != null) {
                    status.setStatus("SYNCING");
                    status.setChecksum(checksum);
                    status.setVersion(status.getVersion() + 1);
                    
                    // 创建更新任务
                    ReplicationTask task = new ReplicationTask(objectKey, 
                            replicationInfo.getPrimaryNode(), replicaNode, "UPDATE");
                    replicationTasks.put(generateTaskId(objectKey, replicaNode), task);
                }
            }
            
            replicationInfo.setLastSyncTime(System.currentTimeMillis());
            
            LOG.info("更新对象副本: key={}, 副本节点数={}", objectKey, replicationInfo.getReplicaNodes().size());
            return true;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 删除对象副本
     */
    public boolean deleteReplicas(String objectKey) {
        lock.writeLock().lock();
        try {
            ReplicationInfo replicationInfo = replicationInfoMap.remove(objectKey);
            if (replicationInfo == null) {
                LOG.warn("对象副本信息不存在: {}", objectKey);
                return false;
            }
            
            // 删除所有副本节点的数据
            for (ClusterNode replicaNode : replicationInfo.getReplicaNodes()) {
                ReplicationTask task = new ReplicationTask(objectKey, 
                        replicationInfo.getPrimaryNode(), replicaNode, "DELETE");
                replicationTasks.put(generateTaskId(objectKey, replicaNode), task);
            }
            
            LOG.info("删除对象副本: key={}, 副本节点数={}", objectKey, replicationInfo.getReplicaNodes().size());
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取对象的可用副本节点
     */
    public List<ClusterNode> getAvailableReplicas(String objectKey) {
        lock.readLock().lock();
        try {
            ReplicationInfo replicationInfo = replicationInfoMap.get(objectKey);
            if (replicationInfo == null) {
                return Collections.emptyList();
            }
            
            List<ClusterNode> availableNodes = new ArrayList<>();
            availableNodes.add(replicationInfo.getPrimaryNode());
            
            // 添加状态为SYNCED的副本节点
            for (Map.Entry<ClusterNode, ReplicaStatus> entry : replicationInfo.getReplicaStatusMap().entrySet()) {
                if ("SYNCED".equals(entry.getValue().getStatus())) {
                    availableNodes.add(entry.getKey());
                }
            }
            
            return availableNodes;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 选择副本节点
     */
    private Set<ClusterNode> selectReplicaNodes(ClusterNode primaryNode, int replicaCount) {
        // 获取所有可用节点
        Set<ClusterNode> allNodes = new HashSet<>();
        Map<String, ShardingManager.ShardMetadata> allShards = shardingManager.getAllShardMetadata();
        for (ShardingManager.ShardMetadata metadata : allShards.values()) {
            allNodes.add(metadata.getPrimaryNode());
            allNodes.addAll(metadata.getReplicaNodes());
        }
        
        // 移除主节点
        allNodes.remove(primaryNode);
        
        // 如果可用节点不足，返回所有可用节点
        if (allNodes.size() <= replicaCount) {
            return allNodes;
        }
        
        // 随机选择副本节点（可以改进为基于负载均衡的选择策略）
        List<ClusterNode> nodeList = new ArrayList<>(allNodes);
        Collections.shuffle(nodeList);
        
        return new HashSet<>(nodeList.subList(0, replicaCount));
    }

    /**
     * 处理副本同步任务
     */
    private void processReplicationTasks() {
        if (replicationTasks.isEmpty()) {
            return;
        }
        
        LOG.debug("处理副本同步任务，待处理任务数: {}", replicationTasks.size());
        
        Iterator<Map.Entry<String, ReplicationTask>> iterator = replicationTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ReplicationTask> entry = iterator.next();
            ReplicationTask task = entry.getValue();
            
            try {
                if ("PENDING".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
                    task.setStatus("RUNNING");
                    
                    // 执行同步任务
                    boolean success = executeReplicationTask(task);
                    
                    if (success) {
                        task.setStatus("COMPLETED");
                        updateReplicaStatus(task.getObjectKey(), task.getTargetNode(), "SYNCED");
                        iterator.remove();
                        LOG.debug("副本同步任务完成: key={}, target={}", 
                                task.getObjectKey(), task.getTargetNode().getStorageServer().getServerId());
                    } else {
                        task.incrementRetryCount();
                        if (task.getRetryCount() >= 3) {
                            task.setStatus("FAILED");
                            updateReplicaStatus(task.getObjectKey(), task.getTargetNode(), "FAILED");
                            iterator.remove();
                            LOG.error("副本同步任务失败，已达最大重试次数: key={}, target={}", 
                                    task.getObjectKey(), task.getTargetNode().getStorageServer().getServerId());
                        } else {
                            task.setStatus("FAILED");
                            LOG.warn("副本同步任务失败，将重试: key={}, target={}, retry={}", 
                                    task.getObjectKey(), task.getTargetNode().getStorageServer().getServerId(), task.getRetryCount());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("处理副本同步任务异常: key={}, target={}", 
                        task.getObjectKey(), task.getTargetNode().getStorageServer().getServerId(), e);
                task.setStatus("FAILED");
            }
        }
    }

    /**
     * 执行副本同步任务
     */
    private boolean executeReplicationTask(ReplicationTask task) {
        try {
            // TODO: 实现具体的副本同步逻辑
            // 这里需要根据具体的存储实现来进行数据同步
            
            switch (task.getOperation()) {
                case "CREATE":
                    return syncCreateReplica(task);
                case "UPDATE":
                    return syncUpdateReplica(task);
                case "DELETE":
                    return syncDeleteReplica(task);
                default:
                    LOG.error("未知的副本同步操作: {}", task.getOperation());
                    return false;
            }
        } catch (Exception e) {
            LOG.error("执行副本同步任务异常", e);
            return false;
        }
    }

    /**
     * 同步创建副本
     */
    private boolean syncCreateReplica(ReplicationTask task) {
        // TODO: 实现创建副本的具体逻辑
        LOG.debug("同步创建副本: key={}, source={}, target={}", 
                task.getObjectKey(), 
                task.getSourceNode().getStorageServer().getServerId(), 
                task.getTargetNode().getStorageServer().getServerId());
        
        // 模拟同步过程
        try {
            Thread.sleep(100); // 模拟网络延迟
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 同步更新副本
     */
    private boolean syncUpdateReplica(ReplicationTask task) {
        // TODO: 实现更新副本的具体逻辑
        LOG.debug("同步更新副本: key={}, source={}, target={}", 
                task.getObjectKey(), 
                task.getSourceNode().getStorageServer().getServerId(), 
                task.getTargetNode().getStorageServer().getServerId());
        
        // 模拟同步过程
        try {
            Thread.sleep(100); // 模拟网络延迟
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 同步删除副本
     */
    private boolean syncDeleteReplica(ReplicationTask task) {
        // TODO: 实现删除副本的具体逻辑
        LOG.debug("同步删除副本: key={}, source={}, target={}", 
                task.getObjectKey(), 
                task.getSourceNode().getStorageServer().getServerId(), 
                task.getTargetNode().getStorageServer().getServerId());
        
        // 模拟同步过程
        try {
            Thread.sleep(50); // 模拟网络延迟
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 更新副本状态
     */
    private void updateReplicaStatus(String objectKey, ClusterNode node, String status) {
        ReplicationInfo replicationInfo = replicationInfoMap.get(objectKey);
        if (replicationInfo != null) {
            ReplicaStatus replicaStatus = replicationInfo.getReplicaStatusMap().get(node);
            if (replicaStatus != null) {
                replicaStatus.setStatus(status);
                replicaStatus.setLastSyncTime(System.currentTimeMillis());
            }
        }
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        LOG.debug("开始执行副本健康检查，副本数: {}", replicationInfoMap.size());
        
        long currentTime = System.currentTimeMillis();
        
        for (ReplicationInfo replicationInfo : replicationInfoMap.values()) {
            int healthyReplicas = 0;
            int totalReplicas = replicationInfo.getReplicaNodes().size() + 1; // +1 for primary
            
            // 检查副本状态
            for (Map.Entry<ClusterNode, ReplicaStatus> entry : replicationInfo.getReplicaStatusMap().entrySet()) {
                ReplicaStatus status = entry.getValue();
                
                // 如果副本长时间未同步，标记为过期
                if (currentTime - status.getLastSyncTime() > syncTimeout * 2) {
                    if (!"FAILED".equals(status.getStatus())) {
                        status.setStatus("STALE");
                        LOG.warn("副本状态过期: key={}, node={}", 
                                replicationInfo.getObjectKey(), entry.getKey().getStorageServer().getServerId());
                    }
                } else if ("SYNCED".equals(status.getStatus())) {
                    healthyReplicas++;
                }
            }
            
            // 更新整体健康状态
            String oldStatus = replicationInfo.getStatus();
            if (healthyReplicas >= totalReplicas * 0.67) { // 2/3以上副本健康
                replicationInfo.setStatus("HEALTHY");
            } else if (healthyReplicas > 0) {
                replicationInfo.setStatus("DEGRADED");
            } else {
                replicationInfo.setStatus("FAILED");
            }
            
            if (!oldStatus.equals(replicationInfo.getStatus())) {
                LOG.info("副本健康状态变更: key={}, {} -> {}, 健康副本数: {}/{}", 
                        replicationInfo.getObjectKey(), oldStatus, replicationInfo.getStatus(),
                        healthyReplicas, totalReplicas);
            }
        }
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId(String objectKey, ClusterNode node) {
        return objectKey + "@" + node.getStorageServer().getServerId();
    }

    /**
     * 获取副本统计信息
     */
    public Map<String, Object> getReplicationStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalObjects", replicationInfoMap.size());
            stats.put("pendingTasks", replicationTasks.size());
            stats.put("replicationFactor", replicationFactor);
            
            int healthyObjects = 0;
            int degradedObjects = 0;
            int failedObjects = 0;
            
            for (ReplicationInfo info : replicationInfoMap.values()) {
                switch (info.getStatus()) {
                    case "HEALTHY":
                        healthyObjects++;
                        break;
                    case "DEGRADED":
                        degradedObjects++;
                        break;
                    case "FAILED":
                        failedObjects++;
                        break;
                }
            }
            
            stats.put("healthyObjects", healthyObjects);
            stats.put("degradedObjects", degradedObjects);
            stats.put("failedObjects", failedObjects);
            stats.put("healthRatio", replicationInfoMap.isEmpty() ? 1.0 : 
                    (double) healthyObjects / replicationInfoMap.size());
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 设置配置参数
     */
    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public void setSyncTimeout(long syncTimeout) {
        this.syncTimeout = syncTimeout;
    }

    public void setHealthCheckInterval(int healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    /**
     * 关闭副本管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("副本管理器已关闭");
    }
} 