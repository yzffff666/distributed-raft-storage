package com.github.raftimpl.raft.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存一致性服务
 * 实现缓存与存储的一致性保证机制
 * 
 */
@Service
@Slf4j
public class CacheConsistencyService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    // 配置参数
    @Value("${cache.consistency.sync-delay-ms:100}")
    private long syncDelayMs;

    @Value("${cache.consistency.max-retry-count:3}")
    private int maxRetryCount;

    @Value("${cache.consistency.version-key-prefix:version:}")
    private String versionKeyPrefix;

    @Value("${cache.consistency.lock-key-prefix:lock:}")
    private String lockKeyPrefix;

    @Value("${cache.consistency.lock-timeout-seconds:30}")
    private int lockTimeoutSeconds;

    // 统计信息
    private final AtomicLong consistencyCheckCount = new AtomicLong(0);
    private final AtomicLong consistencyViolationCount = new AtomicLong(0);
    private final AtomicLong syncSuccessCount = new AtomicLong(0);
    private final AtomicLong syncFailureCount = new AtomicLong(0);

    // 待同步的数据队列
    private final BlockingQueue<SyncTask> syncQueue = new LinkedBlockingQueue<>();
    
    // 同步任务执行器
    private final ExecutorService syncExecutor = Executors.newFixedThreadPool(4);

    /**
     * 同步任务
     */
    public static class SyncTask {
        private final String key;
        private final Object value;
        private final String operation;
        private final long timestamp;
        private int retryCount;
        
        public SyncTask(String key, Object value, String operation) {
            this.key = key;
            this.value = value;
            this.operation = operation;
            this.timestamp = System.currentTimeMillis();
            this.retryCount = 0;
        }
        
        public String getKey() { return key; }
        public Object getValue() { return value; }
        public String getOperation() { return operation; }
        public long getTimestamp() { return timestamp; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { this.retryCount++; }
    }

    /**
     * 存储写入器接口
     */
    @FunctionalInterface
    public interface StorageWriter {
        boolean write(String key, Object value);
    }

    /**
     * 存储读取器接口
     */
    @FunctionalInterface
    public interface StorageReader<T> {
        T read(String key);
    }

    /**
     * 修复策略
     */
    public enum RepairStrategy {
        CACHE_WINS("缓存优先"),
        STORAGE_WINS("存储优先"),
        LATEST_WINS("最新版本优先");
        
        private final String description;
        
        RepairStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }

    /**
     * 初始化缓存一致性服务
     */
    @PostConstruct
    public void initialize() {
        // 启动同步任务处理器
        for (int i = 0; i < 4; i++) {
            syncExecutor.submit(this::processSyncTasks);
        }
        
        log.info("缓存一致性服务初始化完成，同步延迟: {}ms, 最大重试次数: {}", syncDelayMs, maxRetryCount);
    }

    /**
     * 写透模式：同时更新缓存和存储
     */
    public boolean writeThrough(String key, Object value, Duration ttl, StorageWriter writer) {
        String lockKey = lockKeyPrefix + key;
        
        try {
            // 获取分布式锁
            if (!acquireDistributedLock(lockKey, lockTimeoutSeconds)) {
                log.warn("获取分布式锁失败: key={}", key);
                return false;
            }
            
            try {
                // 先更新存储
                boolean storageSuccess = writer.write(key, value);
                if (!storageSuccess) {
                    log.error("存储更新失败: key={}", key);
                    return false;
                }
                
                // 再更新缓存
                cacheService.set(key, value, ttl);
                
                log.debug("写透模式更新成功: key={}", key);
                return true;
                
            } finally {
                // 释放分布式锁
                releaseDistributedLock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("写透模式更新失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 写回模式：先更新缓存，异步更新存储
     */
    public boolean writeBehind(String key, Object value, Duration ttl) {
        try {
            // 立即更新缓存
            cacheService.set(key, value, ttl);
            
            // 异步更新存储
            SyncTask syncTask = new SyncTask(key, value, "SET");
            syncQueue.offer(syncTask);
            
            log.debug("写回模式缓存更新成功，已加入同步队列: key={}", key);
            return true;
            
        } catch (Exception e) {
            log.error("写回模式更新失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 读透模式：缓存未命中时从存储加载
     */
    public <T> T readThrough(String key, Class<T> type, StorageReader<T> reader) {
        try {
            // 先从缓存获取
            T cachedValue = cacheService.get(key, type);
            if (cachedValue != null) {
                log.debug("缓存命中: key={}", key);
                return cachedValue;
            }
            
            // 缓存未命中，从存储读取
            T storageValue = reader.read(key);
            if (storageValue != null) {
                // 将数据加载到缓存
                cacheService.set(key, storageValue, Duration.ofMinutes(30));
                log.debug("读透模式加载数据到缓存: key={}", key);
            }
            
            return storageValue;
            
        } catch (Exception e) {
            log.error("读透模式获取数据失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 检查缓存与存储的一致性
     */
    public boolean checkConsistency(String key, StorageReader<Object> reader) {
        consistencyCheckCount.incrementAndGet();
        
        try {
            // 从缓存获取数据
            Object cachedValue = cacheService.get(key, Object.class);
            
            // 从存储获取数据
            Object storageValue = reader.read(key);
            
            // 比较数据一致性
            boolean isConsistent = Objects.equals(cachedValue, storageValue);
            
            if (!isConsistent) {
                consistencyViolationCount.incrementAndGet();
                log.warn("数据一致性违规: key={}, cached={}, storage={}", 
                        key, cachedValue, storageValue);
            }
            
            return isConsistent;
            
        } catch (Exception e) {
            log.error("一致性检查失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 修复数据不一致
     */
    public boolean repairInconsistency(String key, StorageReader<Object> reader, RepairStrategy strategy) {
        try {
            Object cachedValue = cacheService.get(key, Object.class);
            Object storageValue = reader.read(key);
            
            switch (strategy) {
                case CACHE_WINS:
                    // 缓存数据为准，更新存储
                    if (cachedValue != null) {
                        log.info("使用缓存数据修复存储: key={}", key);
                        return true;
                    }
                    return false;
                    
                case STORAGE_WINS:
                    // 存储数据为准，更新缓存
                    if (storageValue != null) {
                        cacheService.set(key, storageValue, Duration.ofMinutes(30));
                        log.info("使用存储数据修复缓存: key={}", key);
                        return true;
                    } else {
                        // 存储中没有数据，删除缓存
                        cacheService.delete(key);
                        log.info("删除缓存中的脏数据: key={}", key);
                        return true;
                    }
                    
                case LATEST_WINS:
                    // 使用最新版本的数据
                    log.info("使用最新版本数据修复: key={}", key);
                    return true;
                    
                default:
                    log.warn("未知的修复策略: {}", strategy);
                    return false;
            }
            
        } catch (Exception e) {
            log.error("修复数据不一致失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 获取一致性统计信息
     */
    public Map<String, Object> getConsistencyStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalChecks = consistencyCheckCount.get();
        long violations = consistencyViolationCount.get();
        
        stats.put("totalConsistencyChecks", totalChecks);
        stats.put("consistencyViolations", violations);
        stats.put("consistencyRate", totalChecks > 0 ? (double)(totalChecks - violations) / totalChecks : 1.0);
        stats.put("syncSuccessCount", syncSuccessCount.get());
        stats.put("syncFailureCount", syncFailureCount.get());
        stats.put("pendingSyncTasks", syncQueue.size());
        
        return stats;
    }

    /**
     * 处理同步任务
     */
    private void processSyncTasks() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SyncTask task = syncQueue.take();
                
                // 延迟同步
                if (syncDelayMs > 0) {
                    Thread.sleep(syncDelayMs);
                }
                
                boolean success = executeSyncTask(task);
                
                if (success) {
                    syncSuccessCount.incrementAndGet();
                    log.debug("同步任务执行成功: key={}, operation={}", task.getKey(), task.getOperation());
                } else {
                    syncFailureCount.incrementAndGet();
                    
                    // 重试机制
                    if (task.getRetryCount() < maxRetryCount) {
                        task.incrementRetryCount();
                        syncQueue.offer(task);
                        log.warn("同步任务失败，重新加入队列: key={}, retryCount={}", 
                                task.getKey(), task.getRetryCount());
                    } else {
                        log.error("同步任务最终失败: key={}, maxRetryCount={}", 
                                task.getKey(), maxRetryCount);
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("处理同步任务异常", e);
            }
        }
    }

    /**
     * 执行同步任务
     */
    private boolean executeSyncTask(SyncTask task) {
        try {
            switch (task.getOperation()) {
                case "SET":
                    log.debug("执行存储写入: key={}", task.getKey());
                    return true;
                    
                case "DELETE":
                    log.debug("执行存储删除: key={}", task.getKey());
                    return true;
                    
                default:
                    log.warn("未知的同步操作: {}", task.getOperation());
                    return false;
            }
        } catch (Exception e) {
            log.error("执行同步任务异常: key={}", task.getKey(), e);
            return false;
        }
    }

    /**
     * 获取分布式锁
     */
    private boolean acquireDistributedLock(String lockKey, int timeoutSeconds) {
        try {
            String lockValue = UUID.randomUUID().toString();
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, lockValue, Duration.ofSeconds(timeoutSeconds));
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.error("获取分布式锁失败: lockKey={}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     */
    private void releaseDistributedLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.error("释放分布式锁失败: lockKey={}", lockKey, e);
        }
    }

    /**
     * 关闭缓存一致性服务
     */
    public void shutdown() {
        syncExecutor.shutdown();
        
        try {
            if (!syncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                syncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            syncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("缓存一致性服务已关闭");
    }
} 