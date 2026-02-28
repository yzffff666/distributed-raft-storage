package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.service.CacheService;
import com.github.raftimpl.raft.api.service.RedisSentinelHealthService;
import com.github.raftimpl.raft.api.service.impl.StorageServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存管理控制器
 * 提供缓存操作和管理API
 */
@RestController
@RequestMapping("/cache")
@Tag(name = "缓存管理", description = "缓存操作和管理API")
public class CacheController {
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private StorageServiceImpl storageService;
    
    @Autowired(required = false)
    private RedisSentinelHealthService redisSentinelHealthService;
    
    @Operation(summary = "获取缓存值", description = "根据键获取缓存值")
    @GetMapping("/{key}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Object> get(
            @Parameter(description = "缓存键") @PathVariable String key) {
        try {
            Object value = cacheService.get(key, Object.class);
            if (value != null) {
                return ApiResponse.success(value);
            } else {
                return ApiResponse.error("缓存不存在或已过期");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "设置缓存值", description = "设置缓存键值对")
    @PostMapping("/{key}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> set(
            @Parameter(description = "缓存键") @PathVariable String key,
            @Parameter(description = "缓存值") @RequestBody Object value,
            @Parameter(description = "过期时间（秒）") @RequestParam(required = false) Long ttl) {
        try {
            if (ttl != null && ttl > 0) {
                cacheService.set(key, value, Duration.ofSeconds(ttl));
            } else {
                cacheService.set(key, value);
            }
            return ApiResponse.success("缓存设置成功", null);
        } catch (Exception e) {
            return ApiResponse.error("设置缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "删除缓存", description = "删除指定键的缓存")
    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> delete(
            @Parameter(description = "缓存键") @PathVariable String key) {
        try {
            cacheService.delete(key);
            return ApiResponse.success("缓存删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "批量删除缓存", description = "批量删除多个键的缓存")
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> deleteAll(
            @Parameter(description = "缓存键列表") @RequestBody List<String> keys) {
        try {
            cacheService.deleteAll(keys.toArray(new String[0]));
            return ApiResponse.success("批量删除成功", null);
        } catch (Exception e) {
            return ApiResponse.error("批量删除缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "检查缓存是否存在", description = "检查指定键的缓存是否存在")
    @GetMapping("/{key}/exists")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Boolean> exists(
            @Parameter(description = "缓存键") @PathVariable String key) {
        try {
            boolean exists = cacheService.exists(key);
            return ApiResponse.success(exists);
        } catch (Exception e) {
            return ApiResponse.error("检查缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "获取缓存过期时间", description = "获取指定键的缓存剩余过期时间")
    @GetMapping("/{key}/expire")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Long> getExpire(
            @Parameter(description = "缓存键") @PathVariable String key) {
        try {
            long expire = cacheService.getExpire(key);
            return ApiResponse.success(expire);
        } catch (Exception e) {
            return ApiResponse.error("获取过期时间失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "设置缓存过期时间", description = "设置指定键的缓存过期时间")
    @PutMapping("/{key}/expire")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> setExpire(
            @Parameter(description = "缓存键") @PathVariable String key,
            @Parameter(description = "过期时间（秒）") @RequestParam long ttl) {
        try {
            cacheService.expire(key, Duration.ofSeconds(ttl));
            return ApiResponse.success("过期时间设置成功", null);
        } catch (Exception e) {
            return ApiResponse.error("设置过期时间失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "批量获取缓存", description = "批量获取多个键的缓存值")
    @PostMapping("/batch/get")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Map<String, Object>> multiGet(
            @Parameter(description = "缓存键列表") @RequestBody List<String> keys) {
        try {
            Map<String, Object> result = cacheService.multiGet(keys);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error("批量获取缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "批量设置缓存", description = "批量设置多个键值对的缓存")
    @PostMapping("/batch/set")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> multiSet(
            @Parameter(description = "键值对映射") @RequestBody Map<String, Object> keyValues,
            @Parameter(description = "过期时间（秒）") @RequestParam(required = false) Long ttl) {
        try {
            Duration duration = ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : Duration.ofHours(1);
            cacheService.multiSet(keyValues, duration);
            return ApiResponse.success("批量设置成功", null);
        } catch (Exception e) {
            return ApiResponse.error("批量设置缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "获取匹配的缓存键", description = "获取匹配指定模式的所有缓存键")
    @GetMapping("/keys")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Set<String>> keys(
            @Parameter(description = "匹配模式") @RequestParam String pattern) {
        try {
            Set<String> keys = cacheService.keys(pattern);
            return ApiResponse.success(keys);
        } catch (Exception e) {
            return ApiResponse.error("获取缓存键失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "清空所有缓存", description = "清空所有缓存数据")
    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> clear() {
        try {
            cacheService.clear();
            return ApiResponse.success("缓存清空成功", null);
        } catch (Exception e) {
            return ApiResponse.error("清空缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "预热缓存", description = "预热指定键的缓存数据")
    @PostMapping("/warmup")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> warmUp(
            @Parameter(description = "需要预热的键列表") @RequestBody List<String> keys) {
        try {
            // 预热通用缓存
            cacheService.warmUp(keys);
            
            // 预热存储缓存
            storageService.warmUpCache(keys);
            
            return ApiResponse.success("缓存预热成功", null);
        } catch (Exception e) {
            return ApiResponse.error("预热缓存失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "获取缓存统计信息", description = "获取缓存的统计信息")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<CacheService.CacheStats> getStats() {
        try {
            CacheService.CacheStats stats = cacheService.getStats();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            return ApiResponse.error("获取统计信息失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "获取Redis Sentinel健康状态", description = "获取Redis Sentinel的健康检查状态")
    @GetMapping("/sentinel/health")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<RedisSentinelHealthService.HealthReport> getSentinelHealth() {
        try {
            if (redisSentinelHealthService != null) {
                RedisSentinelHealthService.HealthReport report = redisSentinelHealthService.getHealthReport();
                return ApiResponse.success(report);
            } else {
                return ApiResponse.error("Redis Sentinel未启用");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取Sentinel健康状态失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "手动触发Sentinel健康检查", description = "手动触发一次Redis Sentinel健康检查")
    @PostMapping("/sentinel/health/check")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> triggerSentinelHealthCheck() {
        try {
            if (redisSentinelHealthService != null) {
                redisSentinelHealthService.checkHealth();
                return ApiResponse.success("健康检查已触发", null);
            } else {
                return ApiResponse.error("Redis Sentinel未启用");
            }
        } catch (Exception e) {
            return ApiResponse.error("触发健康检查失败: " + e.getMessage());
        }
    }
} 