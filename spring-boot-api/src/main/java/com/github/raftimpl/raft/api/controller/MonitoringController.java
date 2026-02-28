package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.service.impl.StorageServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "监控管理", description = "系统监控和指标查询接口")
public class MonitoringController {
    
    @Autowired
    private StorageServiceImpl storageService;
    
    @GetMapping("/metrics")
    @Operation(summary = "获取系统指标", description = "获取存储系统的性能指标和统计信息")
    public ApiResponse<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // 获取存储统计信息
            Map<String, Object> stats = storageService.getStats();
            metrics.putAll(stats);
            
            // 添加JVM信息
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvmMetrics = new HashMap<>();
            jvmMetrics.put("memory.total", runtime.totalMemory());
            jvmMetrics.put("memory.free", runtime.freeMemory());
            jvmMetrics.put("memory.used", runtime.totalMemory() - runtime.freeMemory());
            jvmMetrics.put("memory.max", runtime.maxMemory());
            jvmMetrics.put("processors", runtime.availableProcessors());
            metrics.put("jvm", jvmMetrics);
            
            // 添加系统信息
            Map<String, Object> systemMetrics = new HashMap<>();
            systemMetrics.put("timestamp", System.currentTimeMillis());
            systemMetrics.put("uptime", System.currentTimeMillis() - getStartTime());
            metrics.put("system", systemMetrics);
            
            return ApiResponse.success("获取系统指标成功", metrics);
        } catch (Exception e) {
            return ApiResponse.error("获取系统指标失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查系统健康状态")
    public ApiResponse<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 检查存储服务是否正常
            boolean storageHealthy = checkStorageHealth();
            
            // 检查JVM内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsage = (double) usedMemory / maxMemory;
            boolean memoryHealthy = memoryUsage < 0.9; // 内存使用率低于90%
            
            boolean overallHealthy = storageHealthy && memoryHealthy;
            
            health.put("status", overallHealthy ? "UP" : "DOWN");
            health.put("storage", storageHealthy ? "UP" : "DOWN");
            health.put("memory", memoryHealthy ? "UP" : "DOWN");
            health.put("memoryUsage", String.format("%.2f%%", memoryUsage * 100));
            health.put("timestamp", System.currentTimeMillis());
            
            return ApiResponse.success("健康检查完成", health);
        } catch (Exception e) {
            Map<String, Object> errorHealth = new HashMap<>();
            errorHealth.put("status", "DOWN");
            errorHealth.put("error", e.getMessage());
            errorHealth.put("timestamp", System.currentTimeMillis());
            return ApiResponse.error("健康检查失败");
        }
    }
    
    private boolean checkStorageHealth() {
        try {
            // 尝试获取存储统计信息来检查存储服务是否正常
            storageService.getStats();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private long getStartTime() {
        // 简单的启动时间估算，实际项目中可以在应用启动时记录
        return System.currentTimeMillis() - 
               java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
    }
}
