package com.github.raftimpl.raft.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 冷存储服务
 * 提供数据分层存储和生命周期管理功能
 * 
 */
@Service
@Slf4j
public class ColdStorageService {

    @Value("${cold-storage.base-path:/tmp/cold-storage}")
    private String basePath;

    @Value("${cold-storage.hot-retention-days:30}")
    private int hotRetentionDays;

    @Value("${cold-storage.warm-retention-days:90}")
    private int warmRetentionDays;

    @Value("${cold-storage.cold-retention-days:365}")
    private int coldRetentionDays;

    // 数据访问统计
    private final Map<String, DataAccessStats> accessStatsMap = new ConcurrentHashMap<>();

    // 数据分层任务执行器
    private final ScheduledExecutorService tieringExecutor = Executors.newScheduledThreadPool(2);

    /**
     * 数据访问统计
     */
    public static class DataAccessStats {
        private final String objectKey;
        private long lastAccessTime;
        private long accessCount;
        private long totalSize;
        private String currentTier; // HOT, WARM, COLD, ARCHIVE
        
        public DataAccessStats(String objectKey) {
            this.objectKey = objectKey;
            this.lastAccessTime = System.currentTimeMillis();
            this.accessCount = 1;
            this.currentTier = "HOT";
        }
        
        public synchronized void recordAccess(long size) {
            this.lastAccessTime = System.currentTimeMillis();
            this.accessCount++;
            this.totalSize = size;
        }
        
        // Getters and Setters
        public String getObjectKey() { return objectKey; }
        public long getLastAccessTime() { return lastAccessTime; }
        public void setLastAccessTime(long lastAccessTime) { this.lastAccessTime = lastAccessTime; }
        public long getAccessCount() { return accessCount; }
        public void setAccessCount(long accessCount) { this.accessCount = accessCount; }
        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
        public String getCurrentTier() { return currentTier; }
        public void setCurrentTier(String currentTier) { this.currentTier = currentTier; }
    }

    /**
     * 数据存储层级
     */
    public enum StorageTier {
        HOT("热存储", "hot", 0),
        WARM("温存储", "warm", 30),
        COLD("冷存储", "cold", 90),
        ARCHIVE("归档存储", "archive", 365);
        
        private final String displayName;
        private final String directory;
        private final int minRetentionDays;
        
        StorageTier(String displayName, String directory, int minRetentionDays) {
            this.displayName = displayName;
            this.directory = directory;
            this.minRetentionDays = minRetentionDays;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDirectory() { return directory; }
        public int getMinRetentionDays() { return minRetentionDays; }
    }

    /**
     * 初始化冷存储服务
     */
    public void initialize() {
        try {
            // 创建存储目录
            for (StorageTier tier : StorageTier.values()) {
                Path tierPath = Paths.get(basePath, tier.getDirectory());
                Files.createDirectories(tierPath);
            }
            
            // 启动数据分层任务
            tieringExecutor.scheduleAtFixedRate(this::performDataTiering, 1, 24, TimeUnit.HOURS);
            
            // 启动统计数据清理任务
            tieringExecutor.scheduleAtFixedRate(this::cleanupOldStats, 1, 7, TimeUnit.DAYS);
            
            log.info("冷存储服务初始化完成，存储路径: {}", basePath);
            
        } catch (Exception e) {
            log.error("冷存储服务初始化失败", e);
            throw new RuntimeException("冷存储服务初始化失败", e);
        }
    }

    /**
     * 存储对象到冷存储
     */
    public boolean storeObject(String objectKey, byte[] data, String tier) {
        try {
            StorageTier storageTier = StorageTier.valueOf(tier.toUpperCase());
            Path objectPath = Paths.get(basePath, storageTier.getDirectory(), objectKey);
            
            // 创建目录
            Files.createDirectories(objectPath.getParent());
            
            // 写入文件
            Files.write(objectPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // 记录访问统计
            recordDataAccess(objectKey, data.length, tier);
            
            log.info("对象存储成功: key={}, tier={}, size={}", objectKey, tier, data.length);
            return true;
            
        } catch (Exception e) {
            log.error("对象存储失败: key={}, tier={}", objectKey, tier, e);
            return false;
        }
    }

    /**
     * 从冷存储获取对象
     */
    public byte[] getObject(String objectKey) {
        try {
            // 按优先级查找对象（热存储 -> 温存储 -> 冷存储 -> 归档存储）
            for (StorageTier tier : StorageTier.values()) {
                Path objectPath = Paths.get(basePath, tier.getDirectory(), objectKey);
                if (Files.exists(objectPath)) {
                    byte[] data = Files.readAllBytes(objectPath);
                    
                    // 记录访问统计
                    recordDataAccess(objectKey, data.length, tier.name());
                    
                    log.info("对象获取成功: key={}, tier={}, size={}", objectKey, tier.name(), data.length);
                    return data;
                }
            }
            
            log.warn("对象不存在: key={}", objectKey);
            return null;
            
        } catch (Exception e) {
            log.error("对象获取失败: key={}", objectKey, e);
            return null;
        }
    }

    /**
     * 删除对象
     */
    public boolean deleteObject(String objectKey) {
        try {
            boolean deleted = false;
            
            // 从所有存储层级删除
            for (StorageTier tier : StorageTier.values()) {
                Path objectPath = Paths.get(basePath, tier.getDirectory(), objectKey);
                if (Files.exists(objectPath)) {
                    Files.delete(objectPath);
                    deleted = true;
                    log.info("对象删除成功: key={}, tier={}", objectKey, tier.name());
                }
            }
            
            // 删除访问统计
            accessStatsMap.remove(objectKey);
            
            return deleted;
            
        } catch (Exception e) {
            log.error("对象删除失败: key={}", objectKey, e);
            return false;
        }
    }

    /**
     * 检查对象是否存在
     */
    public boolean objectExists(String objectKey) {
        try {
            for (StorageTier tier : StorageTier.values()) {
                Path objectPath = Paths.get(basePath, tier.getDirectory(), objectKey);
                if (Files.exists(objectPath)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("检查对象存在性失败: key={}", objectKey, e);
            return false;
        }
    }

    /**
     * 获取对象所在的存储层级
     */
    public String getObjectTier(String objectKey) {
        try {
            for (StorageTier tier : StorageTier.values()) {
                Path objectPath = Paths.get(basePath, tier.getDirectory(), objectKey);
                if (Files.exists(objectPath)) {
                    return tier.name();
                }
            }
            return null;
        } catch (Exception e) {
            log.error("获取对象存储层级失败: key={}", objectKey, e);
            return null;
        }
    }

    /**
     * 手动迁移对象到指定层级
     */
    public boolean migrateObject(String objectKey, String targetTier) {
        try {
            StorageTier target = StorageTier.valueOf(targetTier.toUpperCase());
            
            // 查找源对象
            byte[] data = null;
            String sourceTier = null;
            
            for (StorageTier tier : StorageTier.values()) {
                Path objectPath = Paths.get(basePath, tier.getDirectory(), objectKey);
                if (Files.exists(objectPath)) {
                    data = Files.readAllBytes(objectPath);
                    sourceTier = tier.name();
                    break;
                }
            }
            
            if (data == null) {
                log.warn("源对象不存在: key={}", objectKey);
                return false;
            }
            
            if (targetTier.equals(sourceTier)) {
                log.info("对象已在目标层级: key={}, tier={}", objectKey, targetTier);
                return true;
            }
            
            // 存储到目标层级
            Path targetPath = Paths.get(basePath, target.getDirectory(), objectKey);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            // 删除源对象
            for (StorageTier tier : StorageTier.values()) {
                if (!tier.equals(target)) {
                    Path objectPath = Paths.get(basePath, tier.getDirectory(), objectKey);
                    if (Files.exists(objectPath)) {
                        Files.delete(objectPath);
                    }
                }
            }
            
            // 更新访问统计
            DataAccessStats stats = accessStatsMap.get(objectKey);
            if (stats != null) {
                stats.setCurrentTier(targetTier);
            }
            
            log.info("对象迁移成功: key={}, {} -> {}", objectKey, sourceTier, targetTier);
            return true;
            
        } catch (Exception e) {
            log.error("对象迁移失败: key={}, targetTier={}", objectKey, targetTier, e);
            return false;
        }
    }

    /**
     * 记录数据访问
     */
    public void recordDataAccess(String objectKey, long size, String tier) {
        DataAccessStats stats = accessStatsMap.computeIfAbsent(objectKey, DataAccessStats::new);
        stats.recordAccess(size);
        stats.setCurrentTier(tier);
        
        log.debug("记录数据访问: key={}, size={}, tier={}, accessCount={}", 
                objectKey, size, tier, stats.getAccessCount());
    }

    /**
     * 获取数据访问统计
     */
    public DataAccessStats getDataAccessStats(String objectKey) {
        return accessStatsMap.get(objectKey);
    }

    /**
     * 获取所有数据访问统计
     */
    public Map<String, DataAccessStats> getAllDataAccessStats() {
        return new HashMap<>(accessStatsMap);
    }

    /**
     * 获取存储统计信息
     */
    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Map<String, Integer> tierDistribution = new HashMap<>();
        Map<String, Long> tierSizes = new HashMap<>();
        
        for (DataAccessStats accessStats : accessStatsMap.values()) {
            String tier = accessStats.getCurrentTier();
            tierDistribution.merge(tier, 1, Integer::sum);
            tierSizes.merge(tier, accessStats.getTotalSize(), Long::sum);
        }
        
        stats.put("totalObjects", accessStatsMap.size());
        stats.put("tierDistribution", tierDistribution);
        stats.put("tierSizes", tierSizes);
        stats.put("basePath", basePath);
        
        // 计算磁盘使用情况
        Map<String, Long> diskUsage = new HashMap<>();
        for (StorageTier tier : StorageTier.values()) {
            try {
                Path tierPath = Paths.get(basePath, tier.getDirectory());
                long size = Files.walk(tierPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(path -> {
                            try {
                                return Files.size(path);
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
                diskUsage.put(tier.name(), size);
            } catch (Exception e) {
                diskUsage.put(tier.name(), 0L);
            }
        }
        stats.put("diskUsage", diskUsage);
        
        return stats;
    }

    /**
     * 执行数据分层
     */
    private void performDataTiering() {
        log.info("开始执行数据分层，总对象数: {}", accessStatsMap.size());
        
        long currentTime = System.currentTimeMillis();
        int migratedCount = 0;
        
        for (DataAccessStats stats : accessStatsMap.values()) {
            String newTier = determineOptimalTier(stats, currentTime);
            
            if (!newTier.equals(stats.getCurrentTier())) {
                if (migrateObject(stats.getObjectKey(), newTier)) {
                    migratedCount++;
                    log.info("数据自动迁移: key={}, {} -> {}, lastAccess={}天前", 
                            stats.getObjectKey(), 
                            stats.getCurrentTier(), 
                            newTier,
                            (currentTime - stats.getLastAccessTime()) / (24 * 60 * 60 * 1000));
                }
            }
        }
        
        log.info("数据分层完成，迁移对象数: {}", migratedCount);
    }

    /**
     * 确定最优存储层级
     */
    private String determineOptimalTier(DataAccessStats stats, long currentTime) {
        long daysSinceLastAccess = (currentTime - stats.getLastAccessTime()) / (24 * 60 * 60 * 1000);
        long accessFrequency = stats.getAccessCount();
        
        // 高频访问数据保持在热存储
        if (accessFrequency >= 50) {
            return StorageTier.HOT.name();
        }
        
        // 根据最后访问时间确定层级
        if (daysSinceLastAccess <= hotRetentionDays) {
            return StorageTier.HOT.name();
        } else if (daysSinceLastAccess <= warmRetentionDays) {
            return StorageTier.WARM.name();
        } else if (daysSinceLastAccess <= coldRetentionDays) {
            return StorageTier.COLD.name();
        } else {
            return StorageTier.ARCHIVE.name();
        }
    }

    /**
     * 清理过期统计数据
     */
    private void cleanupOldStats() {
        long currentTime = System.currentTimeMillis();
        long retentionTime = 365L * 24 * 60 * 60 * 1000; // 1年
        
        Iterator<Map.Entry<String, DataAccessStats>> iterator = accessStatsMap.entrySet().iterator();
        int cleanedCount = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<String, DataAccessStats> entry = iterator.next();
            DataAccessStats stats = entry.getValue();
            
            if (currentTime - stats.getLastAccessTime() > retentionTime) {
                iterator.remove();
                cleanedCount++;
            }
        }
        
        log.info("清理过期统计数据完成，清理数量: {}", cleanedCount);
    }

    /**
     * 关闭冷存储服务
     */
    public void shutdown() {
        tieringExecutor.shutdown();
        try {
            if (!tieringExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                tieringExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            tieringExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("冷存储服务已关闭");
    }
} 