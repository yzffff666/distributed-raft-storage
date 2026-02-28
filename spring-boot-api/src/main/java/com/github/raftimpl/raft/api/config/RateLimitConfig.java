package com.github.raftimpl.raft.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 限流配置类
 * 基于Redis的分布式限流实现
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {
    
    private boolean enabled = true;
    private int requestsPerSecond = 100;
    private int burstCapacity = 200;
    
    @Bean
    public RedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local key = KEYS[1] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local tokens = tonumber(ARGV[2]) " +
            "local interval = tonumber(ARGV[3]) " +
            "local current = redis.call('GET', key) " +
            "if current == false then " +
            "    redis.call('SET', key, capacity) " +
            "    redis.call('EXPIRE', key, interval) " +
            "    return capacity - tokens " +
            "else " +
            "    local remaining = tonumber(current) " +
            "    if remaining >= tokens then " +
            "        redis.call('DECRBY', key, tokens) " +
            "        return remaining - tokens " +
            "    else " +
            "        return -1 " +
            "    end " +
            "end"
        );
        script.setResultType(Long.class);
        return script;
    }
    
    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getRequestsPerSecond() {
        return requestsPerSecond;
    }
    
    public void setRequestsPerSecond(int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }
    
    public int getBurstCapacity() {
        return burstCapacity;
    }
    
    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }
} 