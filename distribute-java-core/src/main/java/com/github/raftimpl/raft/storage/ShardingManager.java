package com.github.raftimpl.raft.storage;

import com.github.raftimpl.raft.ClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 数据分片管理器
 * 实现一致性哈希算法进行数据分片，支持动态节点增减
 * 
 */
public class ShardingManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(ShardingManager.class);

    // 虚拟节点数量，用于提高分片均匀性
    private static final int VIRTUAL_NODE_COUNT = 160;
    
    // 哈希环：存储虚拟节点到物理节点的映射
    private final TreeMap<Long, ClusterNode> hashRing = new TreeMap<>();
    
    // 物理节点列表
    private final Set<ClusterNode> physicalNodes = ConcurrentHashMap.newKeySet();
    
    // 分片元数据：存储每个分片的信息
    private final Map<String, ShardMetadata> shardMetadataMap = new ConcurrentHashMap<>();
    
    // 读写锁保护哈希环的一致性
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 分片大小阈值（字节）
    private static final long SHARD_SIZE_THRESHOLD = 64 * 1024 * 1024; // 64MB

    /**
     * 分片元数据
     */
    public static class ShardMetadata {
        private final String shardId;
        private final long createTime;
        private long size;
        private int objectCount;
        private Set<ClusterNode> replicaNodes;
        private ClusterNode primaryNode;
        private String status; // ACTIVE, MIGRATING, READONLY
        
        public ShardMetadata(String shardId, ClusterNode primaryNode) {
            this.shardId = shardId;
            this.primaryNode = primaryNode;
            this.createTime = System.currentTimeMillis();
            this.size = 0;
            this.objectCount = 0;
            this.replicaNodes = new HashSet<>();
            this.status = "ACTIVE";
        }
        
        // Getters and Setters
        public String getShardId() { return shardId; }
        public long getCreateTime() { return createTime; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public int getObjectCount() { return objectCount; }
        public void setObjectCount(int objectCount) { this.objectCount = objectCount; }
        public Set<ClusterNode> getReplicaNodes() { return replicaNodes; }
        public void setReplicaNodes(Set<ClusterNode> replicaNodes) { this.replicaNodes = replicaNodes; }
        public ClusterNode getPrimaryNode() { return primaryNode; }
        public void setPrimaryNode(ClusterNode primaryNode) { this.primaryNode = primaryNode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * 添加节点到哈希环
     */
    public void addNode(ClusterNode node) {
        lock.writeLock().lock();
        try {
            if (physicalNodes.contains(node)) {
                LOG.warn("节点已存在: {}", node.getStorageServer().getServerId());
                return;
            }
            
            physicalNodes.add(node);
            
            // 为每个物理节点创建虚拟节点
            for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
                String virtualNodeKey = node.getStorageServer().getServerId() + "#" + i;
                long hash = hash(virtualNodeKey);
                hashRing.put(hash, node);
            }
            
            LOG.info("节点添加成功: {}, 当前节点数: {}, 虚拟节点数: {}", 
                    node.getStorageServer().getServerId(), physicalNodes.size(), hashRing.size());
            
            // 重新平衡分片
            rebalanceShards();
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从哈希环移除节点
     */
    public void removeNode(ClusterNode node) {
        lock.writeLock().lock();
        try {
            if (!physicalNodes.contains(node)) {
                LOG.warn("节点不存在: {}", node.getStorageServer().getServerId());
                return;
            }
            
            physicalNodes.remove(node);
            
            // 移除所有虚拟节点
            Iterator<Map.Entry<Long, ClusterNode>> iterator = hashRing.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, ClusterNode> entry = iterator.next();
                if (entry.getValue().equals(node)) {
                    iterator.remove();
                }
            }
            
            LOG.info("节点移除成功: {}, 当前节点数: {}, 虚拟节点数: {}", 
                    node.getStorageServer().getServerId(), physicalNodes.size(), hashRing.size());
            
            // 迁移该节点的分片到其他节点
            migrateNodeShards(node);
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 根据键获取负责的节点
     */
    public ClusterNode getNodeForKey(String key) {
        lock.readLock().lock();
        try {
            if (hashRing.isEmpty()) {
                return null;
            }
            
            long hash = hash(key);
            Map.Entry<Long, ClusterNode> entry = hashRing.ceilingEntry(hash);
            
            // 如果没有找到更大的，则返回第一个（环形结构）
            if (entry == null) {
                entry = hashRing.firstEntry();
            }
            
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取键对应的分片ID
     */
    public String getShardId(String key) {
        ClusterNode node = getNodeForKey(key);
        if (node == null) {
            throw new IllegalStateException("没有可用的节点");
        }
        
        // 使用一致性哈希的前8位作为分片ID
        long hash = hash(key);
        String shardId = String.format("shard_%08x", (int)(hash >>> 32));
        
        // 确保分片元数据存在
        shardMetadataMap.computeIfAbsent(shardId, id -> new ShardMetadata(id, node));
        
        return shardId;
    }

    /**
     * 获取分片元数据
     */
    public ShardMetadata getShardMetadata(String shardId) {
        return shardMetadataMap.get(shardId);
    }

    /**
     * 更新分片大小统计
     */
    public void updateShardSize(String shardId, long deltaSize, int deltaObjectCount) {
        ShardMetadata metadata = shardMetadataMap.get(shardId);
        if (metadata != null) {
            metadata.setSize(metadata.getSize() + deltaSize);
            metadata.setObjectCount(metadata.getObjectCount() + deltaObjectCount);
            
            // 检查是否需要分片
            if (metadata.getSize() > SHARD_SIZE_THRESHOLD) {
                LOG.info("分片 {} 大小超过阈值，考虑分裂: size={}MB", 
                        shardId, metadata.getSize() / 1024 / 1024);
                // TODO: 实现分片分裂逻辑
            }
        }
    }

    /**
     * 获取所有分片信息
     */
    public Map<String, ShardMetadata> getAllShardMetadata() {
        return new HashMap<>(shardMetadataMap);
    }

    /**
     * 获取节点负责的分片列表
     */
    public List<String> getNodeShards(ClusterNode node) {
        List<String> shards = new ArrayList<>();
        for (ShardMetadata metadata : shardMetadataMap.values()) {
            if (node.equals(metadata.getPrimaryNode()) || metadata.getReplicaNodes().contains(node)) {
                shards.add(metadata.getShardId());
            }
        }
        return shards;
    }

    /**
     * 重新平衡分片
     */
    private void rebalanceShards() {
        if (physicalNodes.isEmpty()) {
            return;
        }
        
        LOG.info("开始重新平衡分片，当前分片数: {}", shardMetadataMap.size());
        
        // 重新分配分片的主节点
        for (ShardMetadata metadata : shardMetadataMap.values()) {
            ClusterNode newPrimaryNode = getNodeForKey(metadata.getShardId());
            if (!metadata.getPrimaryNode().equals(newPrimaryNode)) {
                LOG.info("分片 {} 主节点变更: {} -> {}", 
                        metadata.getShardId(), 
                        metadata.getPrimaryNode().getStorageServer().getServerId(),
                        newPrimaryNode.getStorageServer().getServerId());
                metadata.setPrimaryNode(newPrimaryNode);
                metadata.setStatus("MIGRATING");
                // TODO: 实现分片迁移逻辑
            }
        }
    }

    /**
     * 迁移节点的分片到其他节点
     */
    private void migrateNodeShards(ClusterNode removedNode) {
        List<String> shardsToMigrate = getNodeShards(removedNode);
        
        LOG.info("开始迁移节点 {} 的分片，分片数: {}", removedNode.getStorageServer().getServerId(), shardsToMigrate.size());
        
        for (String shardId : shardsToMigrate) {
            ShardMetadata metadata = shardMetadataMap.get(shardId);
            if (metadata != null) {
                ClusterNode newPrimaryNode = getNodeForKey(shardId);
                if (newPrimaryNode != null && !newPrimaryNode.equals(removedNode)) {
                    LOG.info("迁移分片 {} 到节点 {}", shardId, newPrimaryNode.getStorageServer().getServerId());
                    metadata.setPrimaryNode(newPrimaryNode);
                    metadata.getReplicaNodes().remove(removedNode);
                    metadata.setStatus("MIGRATING");
                    // TODO: 实现具体的数据迁移逻辑
                }
            }
        }
    }

    /**
     * 计算字符串的哈希值
     */
    private long hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // 将前8个字节转换为long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }

    /**
     * 获取集群统计信息
     */
    public Map<String, Object> getClusterStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("physicalNodeCount", physicalNodes.size());
            stats.put("virtualNodeCount", hashRing.size());
            stats.put("shardCount", shardMetadataMap.size());
            
            long totalSize = 0;
            int totalObjects = 0;
            for (ShardMetadata metadata : shardMetadataMap.values()) {
                totalSize += metadata.getSize();
                totalObjects += metadata.getObjectCount();
            }
            
            stats.put("totalSizeBytes", totalSize);
            stats.put("totalSizeMB", totalSize / 1024.0 / 1024.0);
            stats.put("totalObjects", totalObjects);
            stats.put("avgShardSizeMB", shardMetadataMap.isEmpty() ? 0 : 
                    (totalSize / 1024.0 / 1024.0) / shardMetadataMap.size());
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 检查集群健康状态
     */
    public boolean isClusterHealthy() {
        lock.readLock().lock();
        try {
            if (physicalNodes.isEmpty()) {
                return false;
            }
            
            // 检查是否有分片处于迁移状态过久
            long currentTime = System.currentTimeMillis();
            for (ShardMetadata metadata : shardMetadataMap.values()) {
                if ("MIGRATING".equals(metadata.getStatus())) {
                    // 如果迁移超过10分钟，认为不健康
                    if (currentTime - metadata.getCreateTime() > 10 * 60 * 1000) {
                        LOG.warn("分片 {} 迁移时间过长", metadata.getShardId());
                        return false;
                    }
                }
            }
            
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }
} 