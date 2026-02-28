package com.github.raftimpl.raft.api.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存服务接口
 * 提供智能缓存策略和操作
 */
public interface CacheService {
    
    /**
     * 设置缓存值
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     */
    void set(String key, Object value, Duration ttl);
    
    /**
     * 设置缓存值（使用默认TTL）
     * @param key 缓存键
     * @param value 缓存值
     */
    void set(String key, Object value);
    
    /**
     * 获取缓存值
     * @param key 缓存键
     * @return 缓存值
     */
    <T> T get(String key, Class<T> type);
    
    /**
     * 获取缓存值，如果不存在则执行supplier并缓存结果
     * @param key 缓存键
     * @param type 值类型
     * @param supplier 值提供者
     * @param ttl 过期时间
     */
    <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier, Duration ttl);
    
    /**
     * 删除缓存
     * @param key 缓存键
     */
    void delete(String key);
    
    /**
     * 批量删除缓存
     * @param keys 缓存键列表
     */
    void deleteAll(String... keys);
    
    /**
     * 检查缓存是否存在
     * @param key 缓存键
     * @return 是否存在
     */
    boolean exists(String key);
    
    /**
     * 设置缓存过期时间
     * @param key 缓存键
     * @param ttl 过期时间
     */
    void expire(String key, Duration ttl);
    
    /**
     * 获取缓存剩余过期时间
     * @param key 缓存键
     * @return 剩余时间（秒）
     */
    long getExpire(String key);
    
    /**
     * 批量获取缓存
     * @param keys 缓存键列表
     * @return 键值对映射
     */
    Map<String, Object> multiGet(List<String> keys);
    
    /**
     * 批量设置缓存
     * @param keyValues 键值对映射
     * @param ttl 过期时间
     */
    void multiSet(Map<String, Object> keyValues, Duration ttl);
    
    /**
     * 获取匹配模式的所有键
     * @param pattern 匹配模式
     * @return 键集合
     */
    Set<String> keys(String pattern);
    
    /**
     * 清空所有缓存
     */
    void clear();
    
    /**
     * 预热缓存
     * @param keys 需要预热的键列表
     */
    void warmUp(List<String> keys);
    
    /**
     * 获取缓存统计信息
     * @return 统计信息
     */
    CacheStats getStats();
    
    /**
     * 缓存统计信息
     */
    class CacheStats {
        private long hitCount;
        private long missCount;
        private long totalCount;
        private double hitRate;
        private long evictionCount;
        private long keyCount;
        
        // 构造函数
        public CacheStats(long hitCount, long missCount, long evictionCount, long keyCount) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.totalCount = hitCount + missCount;
            this.hitRate = totalCount > 0 ? (double) hitCount / totalCount : 0.0;
            this.evictionCount = evictionCount;
            this.keyCount = keyCount;
        }
        
        // Getters
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public long getTotalCount() { return totalCount; }
        public double getHitRate() { return hitRate; }
        public long getEvictionCount() { return evictionCount; }
        public long getKeyCount() { return keyCount; }
    }
} 