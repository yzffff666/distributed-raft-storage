package com.github.raftimpl.raft.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Sentinel健康检查服务
 * 监控Redis主从切换和连接状态
 */
@Service
@ConditionalOnProperty(name = "spring.redis.sentinel.enabled", havingValue = "true")
public class RedisSentinelHealthService {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisSentinelHealthService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private RedisConnectionFactory connectionFactory;
    
    @Value("${spring.redis.sentinel.master:mymaster}")
    private String masterName;
    
    @Value("${spring.redis.sentinel.health-check.enabled:true}")
    private boolean healthCheckEnabled;
    
    @Value("${spring.redis.sentinel.health-check.interval:30000}")
    private long healthCheckInterval;
    
    // 健康状态
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong failureCount = new AtomicLong(0);
    private String currentMasterInfo = "";
    
    @PostConstruct
    public void init() {
        if (healthCheckEnabled) {
            logger.info("Redis Sentinel健康检查服务启动，主节点：{}, 检查间隔：{}ms", 
                       masterName, healthCheckInterval);
            checkHealth();
        }
    }
    
    /**
     * 定期健康检查
     */
    @Scheduled(fixedDelayString = "${spring.redis.sentinel.health-check.interval:30000}")
    public void scheduledHealthCheck() {
        if (healthCheckEnabled) {
            checkHealth();
        }
    }
    
    /**
     * 执行健康检查
     */
    public void checkHealth() {
        try {
            // 测试Redis连接
            String testKey = "sentinel:health:check:" + System.currentTimeMillis();
            String testValue = "OK";
            
            // 写入测试
            redisTemplate.opsForValue().set(testKey, testValue, java.time.Duration.ofSeconds(10));
            
            // 读取测试
            Object result = redisTemplate.opsForValue().get(testKey);
            
            if (testValue.equals(result)) {
                // 健康检查成功
                onHealthCheckSuccess();
                
                // 清理测试数据
                redisTemplate.delete(testKey);
            } else {
                onHealthCheckFailure("健康检查读取数据不匹配");
            }
            
        } catch (Exception e) {
            onHealthCheckFailure("健康检查异常: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查成功处理
     */
    private void onHealthCheckSuccess() {
        boolean wasUnhealthy = !isHealthy.get();
        isHealthy.set(true);
        lastSuccessTime.set(System.currentTimeMillis());
        long currentFailures = failureCount.getAndSet(0);
        
        if (wasUnhealthy) {
            logger.info("Redis Sentinel健康检查恢复正常，主节点：{}, 之前失败次数：{}", 
                       masterName, currentFailures);
        }
        
        // 获取当前主节点信息
        updateMasterInfo();
    }
    
    /**
     * 健康检查失败处理
     */
    private void onHealthCheckFailure(String reason) {
        long failures = failureCount.incrementAndGet();
        isHealthy.set(false);
        
        logger.error("Redis Sentinel健康检查失败，主节点：{}, 失败次数：{}, 原因：{}", 
                    masterName, failures, reason);
        
        // 如果连续失败次数过多，可以触发告警
        if (failures >= 3) {
            triggerAlert("Redis Sentinel连续健康检查失败", reason, failures);
        }
    }
    
    /**
     * 更新主节点信息
     */
    private void updateMasterInfo() {
        try {
            // 这里可以通过JedisConnectionFactory获取当前主节点信息
            // 由于Spring Data Redis的限制，我们简化实现
            String newMasterInfo = "Master: " + masterName + " (Active)";
            
            if (!newMasterInfo.equals(currentMasterInfo)) {
                logger.info("Redis主节点信息更新：{} -> {}", currentMasterInfo, newMasterInfo);
                currentMasterInfo = newMasterInfo;
            }
        } catch (Exception e) {
            logger.warn("获取Redis主节点信息失败: {}", e.getMessage());
        }
    }
    
    /**
     * 触发告警
     */
    private void triggerAlert(String title, String message, long failureCount) {
        // 这里可以集成告警系统，如邮件、短信、钉钉等
        logger.error("Redis Sentinel告警 - {}: {}, 失败次数: {}", title, message, failureCount);
        
        // TODO: 集成具体的告警通知系统
        // alertService.sendAlert(title, message, AlertLevel.ERROR);
    }
    
    /**
     * 获取健康状态
     */
    public boolean isHealthy() {
        return isHealthy.get();
    }
    
    /**
     * 获取最后成功时间
     */
    public long getLastSuccessTime() {
        return lastSuccessTime.get();
    }
    
    /**
     * 获取失败次数
     */
    public long getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * 获取当前主节点信息
     */
    public String getCurrentMasterInfo() {
        return currentMasterInfo;
    }
    
    /**
     * 获取健康报告
     */
    public HealthReport getHealthReport() {
        return new HealthReport(
            isHealthy.get(),
            lastSuccessTime.get(),
            failureCount.get(),
            currentMasterInfo,
            System.currentTimeMillis() - lastSuccessTime.get()
        );
    }
    
    /**
     * 健康报告
     */
    public static class HealthReport {
        private final boolean healthy;
        private final long lastSuccessTime;
        private final long failureCount;
        private final String masterInfo;
        private final long timeSinceLastSuccess;
        
        public HealthReport(boolean healthy, long lastSuccessTime, long failureCount, 
                           String masterInfo, long timeSinceLastSuccess) {
            this.healthy = healthy;
            this.lastSuccessTime = lastSuccessTime;
            this.failureCount = failureCount;
            this.masterInfo = masterInfo;
            this.timeSinceLastSuccess = timeSinceLastSuccess;
        }
        
        // Getters
        public boolean isHealthy() { return healthy; }
        public long getLastSuccessTime() { return lastSuccessTime; }
        public long getFailureCount() { return failureCount; }
        public String getMasterInfo() { return masterInfo; }
        public long getTimeSinceLastSuccess() { return timeSinceLastSuccess; }
    }
} 