package com.github.raftimpl.raft.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * 智能缓存策略服务
 * 实现LRU、预热、淘汰等智能缓存策略
 * 
 */
@Service
@Slf4j
public class SmartCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存配置参数
    @Value("${cache.max-size:10000}")
    private int maxCacheSize;

    @Value("${cache.ttl-seconds:3600}")
    private int defaultTtlSeconds;

    @Value("${cache.preload-threshold:0.8}")
    private double preloadThreshold;

    @Value("${cache.hit-ratio-threshold:0.6}")
    private double hitRatioThreshold;

    // 缓存访问统计
    private final Map<String, CacheStats> cacheStatsMap = new ConcurrentHashMap<>();
    
    // 缓存预热任务执行器
    private final ScheduledExecutorService preloadExecutor = Executors.newScheduledThreadPool(2);
    
    // 缓存清理任务执行器
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final String key;
        private long accessCount;
        private long hitCount;
        private long missCount;
        private long lastAccessTime;
        private long lastUpdateTime;
        private int priority; // 缓存优先级：1-低，2-中，3-高
        
        public CacheStats(String key) {
            this.key = key;
            this.accessCount = 0;
            this.hitCount = 0;
            this.missCount = 0;
            this.lastAccessTime = System.currentTimeMillis();
            this.lastUpdateTime = System.currentTimeMillis();
            this.priority = 1; // 默认低优先级
        }
        
        public void recordHit() {
            this.accessCount++;
            this.hitCount++;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public void recordMiss() {
            this.accessCount++;
            this.missCount++;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public void recordUpdate() {
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public double getHitRatio() {
            return accessCount > 0 ? (double) hitCount / accessCount : 0.0;
        }
        
        // Getters and Setters
        public String getKey() { return key; }
        public long getAccessCount() { return accessCount; }
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public long getLastAccessTime() { return lastAccessTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }

    /**
     * 缓存策略类型
     */
    public enum CacheStrategy {
        LRU("最近最少使用"),
        LFU("最不经常使用"),
        FIFO("先进先出"),
        RANDOM("随机淘汰"),
        PRIORITY("优先级淘汰");
        
        private final String description;
        
        CacheStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }

    /**
     * 初始化智能缓存服务
     */
    public void initialize() {
        // 启动缓存预热任务
        preloadExecutor.scheduleAtFixedRate(this::performCachePreload, 5, 60, TimeUnit.MINUTES);
        
        // 启动缓存清理任务
        cleanupExecutor.scheduleAtFixedRate(this::performCacheCleanup, 10, 30, TimeUnit.MINUTES);
        
        // 启动统计数据清理任务
        cleanupExecutor.scheduleAtFixedRate(this::cleanupOldStats, 1, 24, TimeUnit.HOURS);
        
        log.info("智能缓存服务初始化完成，最大缓存大小: {}, 默认TTL: {}秒", maxCacheSize, defaultTtlSeconds);
    }

    /**
     * 智能缓存获取
     */
    public Object getFromCache(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            
            CacheStats stats = cacheStatsMap.computeIfAbsent(key, CacheStats::new);
            
            if (value != null) {
                stats.recordHit();
                log.debug("缓存命中: key={}, hitRatio={}", key, stats.getHitRatio());
                
                // 更新访问时间（用于LRU策略）
                updateAccessTime(key);
                
                return value;
            } else {
                stats.recordMiss();
                log.debug("缓存未命中: key={}, hitRatio={}", key, stats.getHitRatio());
                return null;
            }
            
        } catch (Exception e) {
            log.error("缓存获取失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 智能缓存存储
     */
    public boolean putToCache(String key, Object value, int ttlSeconds, int priority) {
        try {
            // 检查缓存容量，必要时进行淘汰
            if (getCurrentCacheSize() >= maxCacheSize) {
                evictCache(CacheStrategy.LRU, 1);
            }
            
            // 存储到Redis
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(key, value, defaultTtlSeconds, TimeUnit.SECONDS);
            }
            
            // 更新统计信息
            CacheStats stats = cacheStatsMap.computeIfAbsent(key, CacheStats::new);
            stats.recordUpdate();
            stats.setPriority(priority);
            
            // 设置访问时间
            updateAccessTime(key);
            
            log.debug("缓存存储成功: key={}, ttl={}秒, priority={}", key, ttlSeconds, priority);
            return true;
            
        } catch (Exception e) {
            log.error("缓存存储失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 智能缓存存储（使用默认参数）
     */
    public boolean putToCache(String key, Object value) {
        return putToCache(key, value, defaultTtlSeconds, 1);
    }

    /**
     * 缓存预热
     */
    public void preloadCache(List<String> keys, CachePreloadCallback callback) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            int preloadedCount = 0;
            
            for (String key : keys) {
                try {
                    // 检查缓存是否已存在
                    if (!redisTemplate.hasKey(key)) {
                        // 调用回调函数获取数据
                        Object value = callback.loadData(key);
                        if (value != null) {
                            putToCache(key, value, defaultTtlSeconds, 2); // 预热数据设置中等优先级
                            preloadedCount++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("缓存预热失败: key={}", key, e);
                }
            }
            
            log.info("缓存预热完成，预热数据数量: {}/{}", preloadedCount, keys.size());
        }, preloadExecutor);
    }

    /**
     * 缓存预热回调接口
     */
    @FunctionalInterface
    public interface CachePreloadCallback {
        Object loadData(String key);
    }

    /**
     * 缓存淘汰
     */
    public int evictCache(CacheStrategy strategy, int count) {
        try {
            List<String> keysToEvict = selectKeysForEviction(strategy, count);
            
            int evictedCount = 0;
            for (String key : keysToEvict) {
                if (redisTemplate.delete(key)) {
                    cacheStatsMap.remove(key);
                    evictedCount++;
                }
            }
            
            log.info("缓存淘汰完成，策略: {}, 淘汰数量: {}", strategy, evictedCount);
            return evictedCount;
            
        } catch (Exception e) {
            log.error("缓存淘汰失败，策略: {}", strategy, e);
            return 0;
        }
    }

    /**
     * 选择要淘汰的缓存键
     */
    private List<String> selectKeysForEviction(CacheStrategy strategy, int count) {
        List<String> allKeys = new ArrayList<>();
        
        // 获取所有缓存键
        Set<String> redisKeys = redisTemplate.keys("*");
        if (redisKeys != null) {
            allKeys.addAll(redisKeys);
        }
        
        if (allKeys.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> keysToEvict = new ArrayList<>();
        
        switch (strategy) {
            case LRU:
                // 按最后访问时间排序，选择最久未访问的
                allKeys.sort((k1, k2) -> {
                    CacheStats stats1 = cacheStatsMap.get(k1);
                    CacheStats stats2 = cacheStatsMap.get(k2);
                    long time1 = stats1 != null ? stats1.getLastAccessTime() : 0;
                    long time2 = stats2 != null ? stats2.getLastAccessTime() : 0;
                    return Long.compare(time1, time2);
                });
                break;
                
            case LFU:
                // 按访问次数排序，选择访问次数最少的
                allKeys.sort((k1, k2) -> {
                    CacheStats stats1 = cacheStatsMap.get(k1);
                    CacheStats stats2 = cacheStatsMap.get(k2);
                    long count1 = stats1 != null ? stats1.getAccessCount() : 0;
                    long count2 = stats2 != null ? stats2.getAccessCount() : 0;
                    return Long.compare(count1, count2);
                });
                break;
                
            case PRIORITY:
                // 按优先级排序，选择优先级最低的
                allKeys.sort((k1, k2) -> {
                    CacheStats stats1 = cacheStatsMap.get(k1);
                    CacheStats stats2 = cacheStatsMap.get(k2);
                    int priority1 = stats1 != null ? stats1.getPriority() : 1;
                    int priority2 = stats2 != null ? stats2.getPriority() : 1;
                    return Integer.compare(priority1, priority2);
                });
                break;
                
            case RANDOM:
                // 随机选择
                Collections.shuffle(allKeys);
                break;
                
            case FIFO:
            default:
                // 按更新时间排序，选择最早更新的
                allKeys.sort((k1, k2) -> {
                    CacheStats stats1 = cacheStatsMap.get(k1);
                    CacheStats stats2 = cacheStatsMap.get(k2);
                    long time1 = stats1 != null ? stats1.getLastUpdateTime() : 0;
                    long time2 = stats2 != null ? stats2.getLastUpdateTime() : 0;
                    return Long.compare(time1, time2);
                });
                break;
        }
        
        // 选择要淘汰的键，但保护高优先级的数据
        for (String key : allKeys) {
            if (keysToEvict.size() >= count) {
                break;
            }
            
            CacheStats stats = cacheStatsMap.get(key);
            // 保护高优先级数据
            if (stats == null || stats.getPriority() < 3) {
                keysToEvict.add(key);
            }
        }
        
        return keysToEvict;
    }

    /**
     * 更新访问时间
     */
    private void updateAccessTime(String key) {
        try {
            String accessTimeKey = "access_time:" + key;
            redisTemplate.opsForValue().set(accessTimeKey, System.currentTimeMillis(), 
                    defaultTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("更新访问时间失败: key={}", key, e);
        }
    }

    /**
     * 获取当前缓存大小
     */
    private long getCurrentCacheSize() {
        try {
            Set<String> keys = redisTemplate.keys("*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("获取缓存大小失败", e);
            return 0;
        }
    }

    /**
     * 执行缓存预热
     */
    private void performCachePreload() {
        try {
            // 分析热点数据，进行预热
            List<String> hotKeys = identifyHotKeys();
            
            if (!hotKeys.isEmpty()) {
                log.info("开始缓存预热，热点键数量: {}", hotKeys.size());
                
                // 这里可以根据实际业务逻辑实现预热策略
                // 例如：预热访问频率高但缓存命中率低的数据
                for (String key : hotKeys) {
                    CacheStats stats = cacheStatsMap.get(key);
                    if (stats != null && stats.getHitRatio() < hitRatioThreshold) {
                        // TODO: 实现具体的数据加载逻辑
                        log.debug("预热候选键: key={}, hitRatio={}", key, stats.getHitRatio());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("缓存预热执行失败", e);
        }
    }

    /**
     * 识别热点数据
     */
    private List<String> identifyHotKeys() {
        List<String> hotKeys = new ArrayList<>();
        
        // 按访问频率排序，选择访问频率最高的键
        cacheStatsMap.entrySet().stream()
                .filter(entry -> entry.getValue().getAccessCount() > 10) // 访问次数阈值
                .sorted((e1, e2) -> Long.compare(e2.getValue().getAccessCount(), e1.getValue().getAccessCount()))
                .limit(100) // 限制热点键数量
                .forEach(entry -> hotKeys.add(entry.getKey()));
        
        return hotKeys;
    }

    /**
     * 执行缓存清理
     */
    private void performCacheCleanup() {
        try {
            long currentCacheSize = getCurrentCacheSize();
            
            // 如果缓存使用率超过阈值，进行清理
            if (currentCacheSize > maxCacheSize * preloadThreshold) {
                int evictCount = (int) (currentCacheSize - maxCacheSize * 0.7); // 清理到70%
                int evicted = evictCache(CacheStrategy.LRU, evictCount);
                
                log.info("自动缓存清理完成，清理前大小: {}, 清理数量: {}", currentCacheSize, evicted);
            }
            
        } catch (Exception e) {
            log.error("缓存清理执行失败", e);
        }
    }

    /**
     * 清理过期统计数据
     */
    private void cleanupOldStats() {
        long currentTime = System.currentTimeMillis();
        long retentionTime = 7L * 24 * 60 * 60 * 1000; // 7天
        
        Iterator<Map.Entry<String, CacheStats>> iterator = cacheStatsMap.entrySet().iterator();
        int cleanedCount = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<String, CacheStats> entry = iterator.next();
            CacheStats stats = entry.getValue();
            
            // 清理7天未访问的统计数据
            if (currentTime - stats.getLastAccessTime() > retentionTime) {
                iterator.remove();
                cleanedCount++;
            }
        }
        
        log.info("清理过期缓存统计数据完成，清理数量: {}", cleanedCount);
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalAccess = 0;
        long totalHit = 0;
        long totalMiss = 0;
        
        for (CacheStats cacheStats : cacheStatsMap.values()) {
            totalAccess += cacheStats.getAccessCount();
            totalHit += cacheStats.getHitCount();
            totalMiss += cacheStats.getMissCount();
        }
        
        stats.put("totalKeys", cacheStatsMap.size());
        stats.put("currentCacheSize", getCurrentCacheSize());
        stats.put("maxCacheSize", maxCacheSize);
        stats.put("cacheUsageRatio", getCurrentCacheSize() / (double) maxCacheSize);
        stats.put("totalAccess", totalAccess);
        stats.put("totalHit", totalHit);
        stats.put("totalMiss", totalMiss);
        stats.put("overallHitRatio", totalAccess > 0 ? (double) totalHit / totalAccess : 0.0);
        
        return stats;
    }

    /**
     * 获取热点数据统计
     */
    public List<CacheStats> getHotDataStats(int limit) {
        return cacheStatsMap.values().stream()
                .sorted((s1, s2) -> Long.compare(s2.getAccessCount(), s1.getAccessCount()))
                .limit(limit)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 手动刷新缓存
     */
    public boolean refreshCache(String key, Object newValue) {
        try {
            // 删除旧缓存
            redisTemplate.delete(key);
            
            // 存储新值
            return putToCache(key, newValue, defaultTtlSeconds, 2);
            
        } catch (Exception e) {
            log.error("手动刷新缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 关闭智能缓存服务
     */
    public void shutdown() {
        preloadExecutor.shutdown();
        cleanupExecutor.shutdown();
        
        try {
            if (!preloadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                preloadExecutor.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            preloadExecutor.shutdownNow();
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("智能缓存服务已关闭");
    }
} 