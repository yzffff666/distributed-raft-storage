package com.github.raftimpl.raft.api.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.raftimpl.raft.api.service.CacheService;
import com.github.raftimpl.raft.api.service.MonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存服务实现类
 * 基于Redis实现智能缓存策略
 */
@Service
public class CacheServiceImpl implements CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheServiceImpl.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private MonitoringService monitoringService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${cache.redis.key-prefix:raft:cache:}")
    private String keyPrefix;
    
    @Value("${cache.redis.time-to-live:3600000}")
    private long defaultTtlMillis;
    
    // 统计信息
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    
    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            String fullKey = buildKey(key);
            redisTemplate.opsForValue().set(fullKey, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
            logger.debug("缓存设置成功: key={}, ttl={}ms", fullKey, ttl.toMillis());
        } catch (Exception e) {
            logger.error("缓存设置失败: key={}", key, e);
        }
    }
    
    @Override
    public void set(String key, Object value) {
        set(key, value, Duration.ofMillis(defaultTtlMillis));
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        try {
            String fullKey = buildKey(key);
            Object value = redisTemplate.opsForValue().get(fullKey);
            
            if (value != null) {
                hitCount.incrementAndGet();
                monitoringService.recordCacheHit("redis");
                logger.debug("缓存命中: key={}", fullKey);
                
                if (type.isInstance(value)) {
                    return (T) value;
                } else {
                    // 类型转换
                    return convertValue(value, type);
                }
            } else {
                missCount.incrementAndGet();
                monitoringService.recordCacheMiss("redis");
                logger.debug("缓存未命中: key={}", fullKey);
                return null;
            }
        } catch (Exception e) {
            logger.error("缓存获取失败: key={}", key, e);
            missCount.incrementAndGet();
            monitoringService.recordCacheMiss("redis");
            return null;
        }
    }
    
    @Override
    public <T> T getOrCompute(String key, Class<T> type, java.util.function.Supplier<T> supplier, Duration ttl) {
        T value = get(key, type);
        if (value != null) {
            return value;
        }
        
        // 缓存未命中，计算值并缓存
        try {
            value = supplier.get();
            if (value != null) {
                set(key, value, ttl);
                logger.debug("计算并缓存新值: key={}", key);
            }
            return value;
        } catch (Exception e) {
            logger.error("计算缓存值失败: key={}", key, e);
            return null;
        }
    }
    
    @Override
    public void delete(String key) {
        try {
            String fullKey = buildKey(key);
            Boolean deleted = redisTemplate.delete(fullKey);
            if (Boolean.TRUE.equals(deleted)) {
                evictionCount.incrementAndGet();
                logger.debug("缓存删除成功: key={}", fullKey);
            }
        } catch (Exception e) {
            logger.error("缓存删除失败: key={}", key, e);
        }
    }
    
    @Override
    public void deleteAll(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        
        try {
            List<String> fullKeys = new ArrayList<>();
            for (String key : keys) {
                fullKeys.add(buildKey(key));
            }
            
            Long deleted = redisTemplate.delete(fullKeys);
            if (deleted != null && deleted > 0) {
                evictionCount.addAndGet(deleted);
                logger.debug("批量删除缓存成功: count={}", deleted);
            }
        } catch (Exception e) {
            logger.error("批量删除缓存失败: keys={}", Arrays.toString(keys), e);
        }
    }
    
    @Override
    public boolean exists(String key) {
        try {
            String fullKey = buildKey(key);
            Boolean exists = redisTemplate.hasKey(fullKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("检查缓存存在性失败: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public void expire(String key, Duration ttl) {
        try {
            String fullKey = buildKey(key);
            redisTemplate.expire(fullKey, ttl.toMillis(), TimeUnit.MILLISECONDS);
            logger.debug("设置缓存过期时间: key={}, ttl={}ms", fullKey, ttl.toMillis());
        } catch (Exception e) {
            logger.error("设置缓存过期时间失败: key={}", key, e);
        }
    }
    
    @Override
    public long getExpire(String key) {
        try {
            String fullKey = buildKey(key);
            Long expire = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
            return expire != null ? expire : -1;
        } catch (Exception e) {
            logger.error("获取缓存过期时间失败: key={}", key, e);
            return -1;
        }
    }
    
    @Override
    public Map<String, Object> multiGet(List<String> keys) {
        Map<String, Object> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        
        try {
            List<String> fullKeys = new ArrayList<>();
            for (String key : keys) {
                fullKeys.add(buildKey(key));
            }
            
            List<Object> values = redisTemplate.opsForValue().multiGet(fullKeys);
            if (values != null) {
                for (int i = 0; i < keys.size() && i < values.size(); i++) {
                    Object value = values.get(i);
                    if (value != null) {
                        result.put(keys.get(i), value);
                        hitCount.incrementAndGet();
                    } else {
                        missCount.incrementAndGet();
                    }
                }
            }
            
            logger.debug("批量获取缓存: requested={}, found={}", keys.size(), result.size());
        } catch (Exception e) {
            logger.error("批量获取缓存失败: keys={}", keys, e);
        }
        
        return result;
    }
    
    @Override
    public void multiSet(Map<String, Object> keyValues, Duration ttl) {
        if (keyValues == null || keyValues.isEmpty()) {
            return;
        }
        
        try {
            Map<String, Object> fullKeyValues = new HashMap<>();
            for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
                fullKeyValues.put(buildKey(entry.getKey()), entry.getValue());
            }
            
            redisTemplate.opsForValue().multiSet(fullKeyValues);
            
            // 设置过期时间
            for (String fullKey : fullKeyValues.keySet()) {
                redisTemplate.expire(fullKey, ttl.toMillis(), TimeUnit.MILLISECONDS);
            }
            
            logger.debug("批量设置缓存成功: count={}, ttl={}ms", keyValues.size(), ttl.toMillis());
        } catch (Exception e) {
            logger.error("批量设置缓存失败: keyValues={}", keyValues.keySet(), e);
        }
    }
    
    @Override
    public Set<String> keys(String pattern) {
        try {
            String fullPattern = buildKey(pattern);
            Set<String> fullKeys = redisTemplate.keys(fullPattern);
            
            if (fullKeys != null) {
                Set<String> result = new HashSet<>();
                for (String fullKey : fullKeys) {
                    result.add(removeKeyPrefix(fullKey));
                }
                return result;
            }
        } catch (Exception e) {
            logger.error("获取匹配键失败: pattern={}", pattern, e);
        }
        
        return new HashSet<>();
    }
    
    @Override
    public void clear() {
        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                if (deleted != null && deleted > 0) {
                    evictionCount.addAndGet(deleted);
                    logger.info("清空缓存成功: count={}", deleted);
                }
            }
        } catch (Exception e) {
            logger.error("清空缓存失败", e);
        }
    }
    
    @Override
    public void warmUp(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        
        logger.info("开始预热缓存: keys={}", keys.size());
        
        // 这里可以实现具体的预热逻辑
        // 例如从数据库加载数据到缓存
        for (String key : keys) {
            try {
                // 检查缓存是否已存在
                if (!exists(key)) {
                    // 这里可以调用具体的数据加载逻辑
                    logger.debug("预热缓存: key={}", key);
                }
            } catch (Exception e) {
                logger.error("预热缓存失败: key={}", key, e);
            }
        }
        
        logger.info("缓存预热完成");
    }
    
    @Override
    public CacheStats getStats() {
        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            long keyCount = keys != null ? keys.size() : 0;
            
            return new CacheStats(
                hitCount.get(),
                missCount.get(),
                evictionCount.get(),
                keyCount
            );
        } catch (Exception e) {
            logger.error("获取缓存统计信息失败", e);
            return new CacheStats(0, 0, 0, 0);
        }
    }
    
    /**
     * 构建完整的缓存键
     */
    private String buildKey(String key) {
        return keyPrefix + key;
    }
    
    /**
     * 移除键前缀
     */
    private String removeKeyPrefix(String fullKey) {
        if (fullKey.startsWith(keyPrefix)) {
            return fullKey.substring(keyPrefix.length());
        }
        return fullKey;
    }
    
    /**
     * 类型转换
     */
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<T> type) {
        try {
            if (type == String.class) {
                return (T) value.toString();
            } else if (type.isPrimitive() || type.getName().startsWith("java.lang")) {
                // 基本类型和包装类型
                String json = objectMapper.writeValueAsString(value);
                return objectMapper.readValue(json, type);
            } else {
                // 复杂对象
                String json = objectMapper.writeValueAsString(value);
                return objectMapper.readValue(json, type);
            }
        } catch (JsonProcessingException e) {
            logger.error("类型转换失败: value={}, type={}", value, type.getName(), e);
            return null;
        }
    }
} 