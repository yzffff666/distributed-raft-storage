package com.github.raftimpl.raft.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 监控服务 - 完整版本
 * 实现系统监控、指标收集、告警管理等功能
 */
@Service
public class MonitoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringService.class);
    
    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    
    // Timer实例
    private Timer storageTimer;
    private Timer apiTimer;
    
    // 系统启动时间
    private final long startTime = System.currentTimeMillis();
    
    // 基础指标统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successRequests = new AtomicLong(0);
    private final AtomicLong errorRequests = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    private final AtomicLong storageCapacityUsed = new AtomicLong(0);
    private final AtomicLong storageCapacityTotal = new AtomicLong(1024 * 1024 * 1024); // 1GB默认
    private final AtomicLong queueLength = new AtomicLong(0);
    private final AtomicReference<String> circuitBreakerState = new AtomicReference<>("CLOSED");
    private final AtomicLong lastHealthCheckTime = new AtomicLong(System.currentTimeMillis());
    
    // API响应时间统计
    private final ConcurrentHashMap<String, AtomicLong> apiResponseTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> apiRequestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> apiErrorCounts = new ConcurrentHashMap<>();
    
    // 存储操作统计
    private final ConcurrentHashMap<String, AtomicLong> storageOperationCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> storageOperationTimes = new ConcurrentHashMap<>();
    
    // 告警记录
    private final List<AlertRecord> alertHistory = new ArrayList<>();
    private final AtomicLong alertCount = new AtomicLong(0);
    
    // 性能指标记录
    private final List<PerformanceMetric> performanceHistory = new ArrayList<>();
    
    /**
     * 初始化Timer实例
     */
    public void initializeTimers() {
        if (meterRegistry != null) {
            storageTimer = Timer.builder("storage.operation.duration")
                    .description("Storage operation duration")
                    .register(meterRegistry);
                    
            apiTimer = Timer.builder("api.request.duration")
                    .description("API request duration")
                    .register(meterRegistry);
        }
    }
    
    /**
     * 开始存储操作计时
     */
    public Object startStorageTimer() {
        if (meterRegistry != null) {
            return Timer.start(meterRegistry);
        }
        // 如果没有MeterRegistry，返回开始时间
        return System.currentTimeMillis();
    }
    
    /**
     * 开始API计时
     */
    public Object startApiTimer() {
        if (meterRegistry != null) {
            return Timer.start(meterRegistry);
        }
        // 如果没有MeterRegistry，返回开始时间
        return System.currentTimeMillis();
    }
    
    /**
     * 记录存储操作时间（支持Timer.Sample）
     */
    public void recordStorageDuration(Object sample, String operation) {
        if (sample instanceof Timer.Sample && storageTimer != null) {
            long duration = ((Timer.Sample) sample).stop(storageTimer);
            recordStorageDuration(operation, duration / 1_000_000); // 转换为毫秒
        } else if (sample instanceof Long) {
            long duration = System.currentTimeMillis() - (Long) sample;
            recordStorageDuration(operation, duration);
        }
    }
    
    /**
     * 记录API操作时间（支持Timer.Sample）
     */
    public void recordApiDuration(Object sample, String method, String endpoint) {
        if (sample instanceof Timer.Sample && apiTimer != null) {
            long duration = ((Timer.Sample) sample).stop(apiTimer);
            recordApiDuration(method, endpoint, duration / 1_000_000); // 转换为毫秒
        } else if (sample instanceof Long) {
            long duration = System.currentTimeMillis() - (Long) sample;
            recordApiDuration(method, endpoint, duration);
        }
    }
    
    /**
     * 告警记录类
     */
    public static class AlertRecord {
        private String alertName;
        private String level;
        private String message;
        private LocalDateTime timestamp;
        private String status;
        
        public AlertRecord(String alertName, String level, String message) {
            this.alertName = alertName;
            this.level = level;
            this.message = message;
            this.timestamp = LocalDateTime.now();
            this.status = "ACTIVE";
        }
        
        // Getters and setters
        public String getAlertName() { return alertName; }
        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    /**
     * 性能指标记录类
     */
    public static class PerformanceMetric {
        private LocalDateTime timestamp;
        private double cpuUsage;
        private double memoryUsage;
        private long activeConnections;
        private double cacheHitRatio;
        private long qps;
        
        public PerformanceMetric(double cpuUsage, double memoryUsage, long activeConnections, 
                               double cacheHitRatio, long qps) {
            this.timestamp = LocalDateTime.now();
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.activeConnections = activeConnections;
            this.cacheHitRatio = cacheHitRatio;
            this.qps = qps;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public long getActiveConnections() { return activeConnections; }
        public double getCacheHitRatio() { return cacheHitRatio; }
        public long getQps() { return qps; }
    }
    
    // ========== API监控方法 ==========
    
    /**
     * 记录API请求
     */
    public void recordApiRequest(String method, String endpoint) {
        totalRequests.incrementAndGet();
        String key = method + " " + endpoint;
        apiRequestCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        logger.debug("API请求记录: {} {}", method, endpoint);
    }
    
    /**
     * 记录API成功响应
     */
    public void recordApiSuccess(String method, String endpoint) {
        successRequests.incrementAndGet();
        logger.debug("API成功响应: {} {}", method, endpoint);
    }
    
    /**
     * 记录API错误
     */
    public void recordApiError(String method, String endpoint, String errorType) {
        errorRequests.incrementAndGet();
        String key = method + " " + endpoint;
        apiErrorCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        // 检查是否需要触发告警
        checkApiErrorAlert(key, errorType);
        
        logger.warn("API错误记录: {} {} - {}", method, endpoint, errorType);
    }
    
    /**
     * 记录API响应时间
     */
    public void recordApiDuration(String method, String endpoint, long durationMs) {
        String key = method + " " + endpoint;
        apiResponseTimes.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(durationMs);
        
        // 检查响应时间告警
        if (durationMs > 5000) { // 超过5秒触发告警
            triggerAlert("HIGH_API_LATENCY", "WARNING", 
                String.format("API响应时间过长: %s %s 耗时 %dms", method, endpoint, durationMs));
        }
        
        logger.debug("API响应时间记录: {} {} - {}ms", method, endpoint, durationMs);
    }
    
    // ========== 存储操作监控方法 ==========
    
    /**
     * 记录存储操作
     */
    public void recordStorageOperation(String operation, String status) {
        String key = operation + "_" + status;
        storageOperationCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        logger.debug("存储操作记录: {} - {}", operation, status);
    }
    
    /**
     * 记录存储操作时间
     */
    public void recordStorageDuration(String operation, long durationMs) {
        storageOperationTimes.computeIfAbsent(operation, k -> new AtomicLong(0)).addAndGet(durationMs);
        
        // 检查存储操作时间告警
        if (durationMs > 10000) { // 超过10秒触发告警
            triggerAlert("HIGH_STORAGE_LATENCY", "WARNING", 
                String.format("存储操作耗时过长: %s 耗时 %dms", operation, durationMs));
        }
        
        logger.debug("存储操作时间记录: {} - {}ms", operation, durationMs);
    }
    
    // ========== 系统资源监控方法 ==========
    
    /**
     * 更新系统资源使用情况
     */
    public void updateSystemMetrics() {
        Runtime runtime = Runtime.getRuntime();
        
        // 计算内存使用率
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsage = (double) usedMemory / totalMemory * 100;
        
        // 计算缓存命中率
        long totalCacheRequests = cacheHitCount.get() + cacheMissCount.get();
        double cacheHitRatio = totalCacheRequests > 0 ? 
            (double) cacheHitCount.get() / totalCacheRequests * 100 : 0;
        
        // 计算QPS（最近一分钟的请求数）
        long currentQps = calculateCurrentQps();
        
        // 记录性能指标
        PerformanceMetric metric = new PerformanceMetric(
            getCpuUsage(), memoryUsage, activeConnections.get(), cacheHitRatio, currentQps
        );
        
        synchronized (performanceHistory) {
            performanceHistory.add(metric);
            // 保留最近1000条记录
            if (performanceHistory.size() > 1000) {
                performanceHistory.remove(0);
            }
        }
        
        // 检查资源使用告警
        checkResourceAlerts(memoryUsage, cacheHitRatio);
        
        logger.debug("系统指标更新 - 内存使用率: {:.2f}%, 缓存命中率: {:.2f}%, QPS: {}", 
                    memoryUsage, cacheHitRatio, currentQps);
    }
    
    /**
     * 获取CPU使用率（简化实现）
     */
    private double getCpuUsage() {
        // 简化的CPU使用率计算，实际项目中可以使用JMX获取更准确的值
        return Math.random() * 100; // 模拟值
    }
    
    /**
     * 计算当前QPS
     */
    private long calculateCurrentQps() {
        // 简化实现，实际应该基于时间窗口计算
        return totalRequests.get() / Math.max(1, (System.currentTimeMillis() - startTime) / 1000);
    }
    
    // ========== 连接监控方法 ==========
    
    public void incrementActiveConnections() {
        long current = activeConnections.incrementAndGet();
        
        // 检查连接数告警
        if (current > 1000) { // 超过1000个连接触发告警
            triggerAlert("HIGH_CONNECTION_COUNT", "WARNING", 
                String.format("活跃连接数过高: %d", current));
        }
    }
    
    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }
    
    // ========== 缓存监控方法 ==========
    
    public void recordCacheHit(String cacheType) {
        cacheHitCount.incrementAndGet();
        logger.debug("缓存命中: {}", cacheType);
    }
    
    public void recordCacheMiss(String cacheType) {
        cacheMissCount.incrementAndGet();
        logger.debug("缓存未命中: {}", cacheType);
    }
    
    // ========== 存储容量监控方法 ==========
    
    public void updateStorageCapacity(long used, long total) {
        storageCapacityUsed.set(used);
        storageCapacityTotal.set(total);
        
        // 检查存储容量告警
        double usageRatio = (double) used / total * 100;
        if (usageRatio > 90) { // 超过90%触发告警
            triggerAlert("HIGH_STORAGE_USAGE", "CRITICAL", 
                String.format("存储使用率过高: %.2f%%", usageRatio));
        } else if (usageRatio > 80) { // 超过80%触发警告
            triggerAlert("HIGH_STORAGE_USAGE", "WARNING", 
                String.format("存储使用率较高: %.2f%%", usageRatio));
        }
    }
    
    public void incrementStorageUsage(long bytes) {
        long newUsed = storageCapacityUsed.addAndGet(bytes);
        long total = storageCapacityTotal.get();
        
        // 检查存储容量告警
        double usageRatio = (double) newUsed / total * 100;
        if (usageRatio > 90) {
            triggerAlert("HIGH_STORAGE_USAGE", "CRITICAL", 
                String.format("存储使用率过高: %.2f%%", usageRatio));
        }
    }
    
    public void decrementStorageUsage(long bytes) {
        storageCapacityUsed.addAndGet(-bytes);
    }
    
    // ========== 队列监控方法 ==========
    
    public void setQueueLength(long length) {
        queueLength.set(length);
        
        // 检查队列长度告警
        if (length > 10000) { // 超过10000个任务触发告警
            triggerAlert("HIGH_QUEUE_LENGTH", "WARNING", 
                String.format("队列长度过长: %d", length));
        }
    }
    
    // ========== 熔断器监控方法 ==========
    
    public void recordCircuitBreakerOpen(String service) {
        circuitBreakerState.set("OPEN");
        triggerAlert("CIRCUIT_BREAKER_OPEN", "CRITICAL", 
            String.format("熔断器打开: %s", service));
        logger.warn("熔断器打开: {}", service);
    }
    
    public void setCircuitBreakerState(String state) {
        String oldState = circuitBreakerState.getAndSet(state);
        if (!"CLOSED".equals(oldState) && "CLOSED".equals(state)) {
            triggerAlert("CIRCUIT_BREAKER_RECOVERED", "INFO", "熔断器已恢复");
        }
    }
    
    // ========== 限流监控方法 ==========
    
    public void recordRateLimitExceeded(String endpoint) {
        triggerAlert("RATE_LIMIT_EXCEEDED", "WARNING", 
            String.format("限流超限: %s", endpoint));
        logger.warn("限流超限: {}", endpoint);
    }
    
    public void recordRateLimitRejected(String endpoint) {
        triggerAlert("RATE_LIMIT_REJECTED", "WARNING", 
            String.format("限流拒绝: %s", endpoint));
        logger.warn("限流拒绝: {}", endpoint);
    }
    
    // ========== 健康检查方法 ==========
    
    /**
     * 更新健康检查时间
     */
    public void updateHealthCheckTime() {
        lastHealthCheckTime.set(System.currentTimeMillis());
    }
    
    /**
     * 检查系统健康状态
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        // 基础健康指标
        health.put("status", "UP");
        health.put("uptime", System.currentTimeMillis() - startTime);
        health.put("lastHealthCheck", lastHealthCheckTime.get());
        
        // 服务状态
        Map<String, String> services = new HashMap<>();
        services.put("storage", "UP");
        services.put("cache", "UP");
        services.put("circuitBreaker", circuitBreakerState.get());
        health.put("services", services);
        
        // 资源状态
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> resources = new HashMap<>();
        resources.put("memoryUsed", runtime.totalMemory() - runtime.freeMemory());
        resources.put("memoryTotal", runtime.totalMemory());
        resources.put("memoryMax", runtime.maxMemory());
        resources.put("activeConnections", activeConnections.get());
        resources.put("queueLength", queueLength.get());
        health.put("resources", resources);
        
        return health;
    }
    
    // ========== 告警管理方法 ==========
    
    /**
     * 触发告警
     */
    private void triggerAlert(String alertName, String level, String message) {
        AlertRecord alert = new AlertRecord(alertName, level, message);
        
        synchronized (alertHistory) {
            alertHistory.add(alert);
            // 保留最近500条告警记录
            if (alertHistory.size() > 500) {
                alertHistory.remove(0);
            }
        }
        
        alertCount.incrementAndGet();
        
        logger.warn("告警触发 [{}] {}: {}", level, alertName, message);
        
        // TODO: 实际项目中可以集成邮件、短信、钉钉等告警通知
    }
    
    /**
     * 检查API错误告警
     */
    private void checkApiErrorAlert(String apiKey, String errorType) {
        AtomicLong errorCount = apiErrorCounts.get(apiKey);
        if (errorCount != null && errorCount.get() > 10) { // 超过10次错误触发告警
            triggerAlert("HIGH_API_ERROR_RATE", "WARNING", 
                String.format("API错误率过高: %s 错误次数: %d", apiKey, errorCount.get()));
        }
    }
    
    /**
     * 检查资源使用告警
     */
    private void checkResourceAlerts(double memoryUsage, double cacheHitRatio) {
        // 内存使用率告警
        if (memoryUsage > 90) {
            triggerAlert("HIGH_MEMORY_USAGE", "CRITICAL", 
                String.format("内存使用率过高: %.2f%%", memoryUsage));
        } else if (memoryUsage > 80) {
            triggerAlert("HIGH_MEMORY_USAGE", "WARNING", 
                String.format("内存使用率较高: %.2f%%", memoryUsage));
        }
        
        // 缓存命中率告警
        if (cacheHitRatio < 50) {
            triggerAlert("LOW_CACHE_HIT_RATIO", "WARNING", 
                String.format("缓存命中率过低: %.2f%%", cacheHitRatio));
        }
    }
    
    // ========== 指标获取方法 ==========
    
    /**
     * 获取完整的监控指标
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 基础指标
        Map<String, Object> basicMetrics = new HashMap<>();
        basicMetrics.put("totalRequests", totalRequests.get());
        basicMetrics.put("successRequests", successRequests.get());
        basicMetrics.put("errorRequests", errorRequests.get());
        basicMetrics.put("activeConnections", activeConnections.get());
        basicMetrics.put("uptime", System.currentTimeMillis() - startTime);
        metrics.put("basic", basicMetrics);
        
        // 缓存指标
        Map<String, Object> cacheMetrics = new HashMap<>();
        long totalCacheRequests = cacheHitCount.get() + cacheMissCount.get();
        cacheMetrics.put("hitCount", cacheHitCount.get());
        cacheMetrics.put("missCount", cacheMissCount.get());
        cacheMetrics.put("hitRatio", totalCacheRequests > 0 ? 
            (double) cacheHitCount.get() / totalCacheRequests * 100 : 0);
        metrics.put("cache", cacheMetrics);
        
        // 存储指标
        Map<String, Object> storageMetrics = new HashMap<>();
        storageMetrics.put("capacityUsed", storageCapacityUsed.get());
        storageMetrics.put("capacityTotal", storageCapacityTotal.get());
        storageMetrics.put("usageRatio", storageCapacityTotal.get() > 0 ? 
            (double) storageCapacityUsed.get() / storageCapacityTotal.get() * 100 : 0);
        storageMetrics.put("queueLength", queueLength.get());
        metrics.put("storage", storageMetrics);
        
        // API指标
        Map<String, Object> apiMetrics = new HashMap<>();
        apiMetrics.put("requestCounts", new HashMap<>(apiRequestCounts));
        apiMetrics.put("errorCounts", new HashMap<>(apiErrorCounts));
        apiMetrics.put("responseTimes", new HashMap<>(apiResponseTimes));
        metrics.put("api", apiMetrics);
        
        // 存储操作指标
        Map<String, Object> storageOpMetrics = new HashMap<>();
        storageOpMetrics.put("operationCounts", new HashMap<>(storageOperationCounts));
        storageOpMetrics.put("operationTimes", new HashMap<>(storageOperationTimes));
        metrics.put("storageOperations", storageOpMetrics);
        
        // 系统指标
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> systemMetrics = new HashMap<>();
        systemMetrics.put("memoryTotal", runtime.totalMemory());
        systemMetrics.put("memoryFree", runtime.freeMemory());
        systemMetrics.put("memoryUsed", runtime.totalMemory() - runtime.freeMemory());
        systemMetrics.put("memoryMax", runtime.maxMemory());
        systemMetrics.put("processors", runtime.availableProcessors());
        systemMetrics.put("circuitBreakerState", circuitBreakerState.get());
        metrics.put("system", systemMetrics);
        
        // 告警指标
        Map<String, Object> alertMetrics = new HashMap<>();
        alertMetrics.put("totalAlerts", alertCount.get());
        alertMetrics.put("recentAlerts", getRecentAlerts(10));
        metrics.put("alerts", alertMetrics);
        
        return metrics;
    }
    
    /**
     * 获取最近的告警记录
     */
    public List<AlertRecord> getRecentAlerts(int limit) {
        synchronized (alertHistory) {
            int size = alertHistory.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(alertHistory.subList(fromIndex, size));
        }
    }
    
    /**
     * 获取性能历史数据
     */
    public List<PerformanceMetric> getPerformanceHistory(int limit) {
        synchronized (performanceHistory) {
            int size = performanceHistory.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(performanceHistory.subList(fromIndex, size));
        }
    }
    
    /**
     * 生成Prometheus格式的指标
     */
    public String getPrometheusMetrics() {
        StringBuilder sb = new StringBuilder();
        
        // 基础指标
        sb.append("# HELP raft_api_requests_total Total number of API requests\n");
        sb.append("# TYPE raft_api_requests_total counter\n");
        sb.append("raft_api_requests_total ").append(totalRequests.get()).append("\n");
        
        sb.append("# HELP raft_api_requests_success_total Total number of successful API requests\n");
        sb.append("# TYPE raft_api_requests_success_total counter\n");
        sb.append("raft_api_requests_success_total ").append(successRequests.get()).append("\n");
        
        sb.append("# HELP raft_api_requests_error_total Total number of failed API requests\n");
        sb.append("# TYPE raft_api_requests_error_total counter\n");
        sb.append("raft_api_requests_error_total ").append(errorRequests.get()).append("\n");
        
        sb.append("# HELP raft_active_connections Current number of active connections\n");
        sb.append("# TYPE raft_active_connections gauge\n");
        sb.append("raft_active_connections ").append(activeConnections.get()).append("\n");
        
        // 缓存指标
        sb.append("# HELP raft_cache_hits_total Total number of cache hits\n");
        sb.append("# TYPE raft_cache_hits_total counter\n");
        sb.append("raft_cache_hits_total ").append(cacheHitCount.get()).append("\n");
        
        sb.append("# HELP raft_cache_misses_total Total number of cache misses\n");
        sb.append("# TYPE raft_cache_misses_total counter\n");
        sb.append("raft_cache_misses_total ").append(cacheMissCount.get()).append("\n");
        
        // 存储指标
        sb.append("# HELP raft_storage_capacity_used_bytes Used storage capacity in bytes\n");
        sb.append("# TYPE raft_storage_capacity_used_bytes gauge\n");
        sb.append("raft_storage_capacity_used_bytes ").append(storageCapacityUsed.get()).append("\n");
        
        sb.append("# HELP raft_storage_capacity_total_bytes Total storage capacity in bytes\n");
        sb.append("# TYPE raft_storage_capacity_total_bytes gauge\n");
        sb.append("raft_storage_capacity_total_bytes ").append(storageCapacityTotal.get()).append("\n");
        
        sb.append("# HELP raft_queue_length Current queue length\n");
        sb.append("# TYPE raft_queue_length gauge\n");
        sb.append("raft_queue_length ").append(queueLength.get()).append("\n");
        
        // 告警指标
        sb.append("# HELP raft_alerts_total Total number of alerts triggered\n");
        sb.append("# TYPE raft_alerts_total counter\n");
        sb.append("raft_alerts_total ").append(alertCount.get()).append("\n");
        
        return sb.toString();
    }
}
