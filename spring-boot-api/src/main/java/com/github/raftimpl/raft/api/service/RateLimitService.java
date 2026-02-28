package com.github.raftimpl.raft.api.service;

import com.github.raftimpl.raft.api.config.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RateLimitService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private RedisScript<Long> rateLimitScript;
    
    @Autowired
    private RateLimitConfig rateLimitConfig;
    
    /**
     * 检查是否允许访问
     * @param key 限流键
     * @param tokens 需要的token数量
     * @return true表示允许访问，false表示被限流
     */
    public boolean isAllowed(String key, int tokens) {
        if (!rateLimitConfig.isEnabled()) {
            return true;
        }
        
        try {
            Long remaining = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                rateLimitConfig.getBurstCapacity(),
                tokens,
                1 // 1秒间隔
            );
            
            if (remaining != null && remaining >= 0) {
                logger.debug("Rate limit check passed for key: {}, remaining tokens: {}", key, remaining);
                return true;
            } else {
                logger.warn("Rate limit exceeded for key: {}", key);
                return false;
            }
        } catch (Exception e) {
            logger.error("Rate limit check failed for key: {}", key, e);
            // 如果Redis出现问题，默认允许访问
            return true;
        }
    }
    
    /**
     * 获取限流键
     * @param identifier 标识符（IP地址、用户ID等）
     * @param endpoint 端点
     * @return 限流键
     */
    public String getRateLimitKey(String identifier, String endpoint) {
        return "rate_limit:" + identifier + ":" + endpoint;
    }
}
