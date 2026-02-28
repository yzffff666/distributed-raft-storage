package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.service.AsyncProcessingService;
import com.github.raftimpl.raft.api.service.LoadBalancerService;
import com.github.raftimpl.raft.api.service.NetworkOptimizationService;
import com.github.raftimpl.raft.api.service.PerformanceTestService;
import com.github.raftimpl.raft.api.service.SmartRoutingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 性能优化控制器
 * 提供性能优化管理的REST API接口
 * 
 */
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceOptimizationController {

    @Autowired(required = false)
    private LoadBalancerService loadBalancerService;

    @Autowired(required = false)
    private NetworkOptimizationService networkOptimizationService;

    @Autowired(required = false)
    private AsyncProcessingService asyncProcessingService;

    @Autowired(required = false)
    private PerformanceTestService performanceTestService;

    @Autowired(required = false)
    private SmartRoutingService smartRoutingService;

    /**
     * 获取性能优化总览
     * 
     * @return 性能优化总览信息
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getPerformanceOverview() {
        Map<String, Object> overview = new HashMap<>();
        
        // 负载均衡统计
        if (loadBalancerService != null) {
            overview.put("loadBalancer", "已启用");
            overview.put("availableNodes", loadBalancerService.getAvailableNodes().size());
        } else {
            overview.put("loadBalancer", "未启用");
        }
        
        // 网络优化统计
        if (networkOptimizationService != null) {
            NetworkOptimizationService.NetworkStats networkStats = networkOptimizationService.getNetworkStats();
            overview.put("networkOptimization", "已启用");
            overview.put("networkStats", networkStats);
        } else {
            overview.put("networkOptimization", "未启用");
        }
        
        // 异步处理统计
        if (asyncProcessingService != null) {
            AsyncProcessingService.AsyncStats asyncStats = asyncProcessingService.getAsyncStats();
            overview.put("asyncProcessing", "已启用");
            overview.put("asyncStats", asyncStats);
        } else {
            overview.put("asyncProcessing", "未启用");
        }
        
        // 智能路由统计
        if (smartRoutingService != null) {
            SmartRoutingService.RoutingStats routingStats = smartRoutingService.getRoutingStats();
            overview.put("smartRouting", "已启用");
            overview.put("routingStats", routingStats);
        } else {
            overview.put("smartRouting", "未启用");
        }
        
        overview.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(overview);
    }

    /**
     * 获取网络优化配置
     * 
     * @return 网络优化配置
     */
    @GetMapping("/network/config")
    public ResponseEntity<Map<String, Object>> getNetworkConfig() {
        if (networkOptimizationService == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "网络优化服务未启用");
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put("compressionEnabled", true);
        config.put("pipeliningEnabled", true);
        config.put("connectionPoolSize", 100);
        config.put("batchingEnabled", true);
        
        return ResponseEntity.ok(config);
    }

    /**
     * 更新网络优化配置
     * 
     * @param config 配置参数
     * @return 操作结果
     */
    @PutMapping("/network/config")
    public ResponseEntity<Map<String, Object>> updateNetworkConfig(@RequestBody Map<String, Object> config) {
        if (networkOptimizationService == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "网络优化服务未启用");
            return ResponseEntity.badRequest().body(error);
        }
        
        // 更新连接池大小
        if (config.containsKey("connectionPoolSize")) {
            int poolSize = ((Number) config.get("connectionPoolSize")).intValue();
            networkOptimizationService.enableConnectionPool(poolSize);
        }
        
        // 更新管道化设置
        if (config.containsKey("pipeliningEnabled")) {
            boolean enabled = (Boolean) config.get("pipeliningEnabled");
            networkOptimizationService.enablePipelining(enabled);
        }
        
        // 更新超时设置
        if (config.containsKey("connectTimeout") && config.containsKey("readTimeout")) {
            int connectTimeout = ((Number) config.get("connectTimeout")).intValue();
            int readTimeout = ((Number) config.get("readTimeout")).intValue();
            networkOptimizationService.setNetworkTimeout(connectTimeout, readTimeout);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "网络优化配置已更新");
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取异步处理配置
     * 
     * @return 异步处理配置
     */
    @GetMapping("/async/config")
    public ResponseEntity<Map<String, Object>> getAsyncConfig() {
        if (asyncProcessingService == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "异步处理服务未启用");
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put("batchSize", 100);
        config.put("batchDelayMs", 50);
        config.put("threadPoolSize", 50);
        
        return ResponseEntity.ok(config);
    }

    /**
     * 更新异步处理配置
     * 
     * @param config 配置参数
     * @return 操作结果
     */
    @PutMapping("/async/config")
    public ResponseEntity<Map<String, Object>> updateAsyncConfig(@RequestBody Map<String, Object> config) {
        if (asyncProcessingService == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "异步处理服务未启用");
            return ResponseEntity.badRequest().body(error);
        }
        
        // 更新批处理配置
        if (config.containsKey("batchSize") && config.containsKey("batchDelayMs")) {
            int batchSize = ((Number) config.get("batchSize")).intValue();
            int batchDelayMs = ((Number) config.get("batchDelayMs")).intValue();
            asyncProcessingService.setBatchConfig(batchSize, batchDelayMs);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "异步处理配置已更新");
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 执行性能测试
     * 
     * @param testConfig 测试配置
     * @return 测试结果
     */
    @PostMapping("/test")
    public ResponseEntity<CompletableFuture<PerformanceTestService.TestResult>> runPerformanceTest(
            @RequestBody PerformanceTestService.TestConfig testConfig) {
        
        if (performanceTestService == null) {
            return ResponseEntity.notFound().build();
        }
        
        CompletableFuture<PerformanceTestService.TestResult> testResult;
        
        switch (testConfig.getTestName()) {
            case "write":
                testResult = performanceTestService.runWritePerformanceTest(testConfig);
                break;
            case "read":
                testResult = performanceTestService.runReadPerformanceTest(testConfig);
                break;
            case "mixed":
                testResult = performanceTestService.runMixedPerformanceTest(testConfig);
                break;
            case "concurrency":
                testResult = performanceTestService.runConcurrencyStressTest(testConfig);
                break;
            case "memory":
                testResult = performanceTestService.runMemoryStressTest(testConfig);
                break;
            case "latency":
                testResult = performanceTestService.runNetworkLatencyTest(testConfig);
                break;
            default:
                testResult = performanceTestService.runMixedPerformanceTest(testConfig);
        }
        
        return ResponseEntity.ok(testResult);
    }

    /**
     * 获取系统基准指标
     * 
     * @return 基准指标
     */
    @GetMapping("/benchmark")
    public ResponseEntity<PerformanceTestService.BenchmarkMetrics> getBenchmarkMetrics() {
        if (performanceTestService == null) {
            return ResponseEntity.notFound().build();
        }
        
        PerformanceTestService.BenchmarkMetrics metrics = performanceTestService.getBenchmarkMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * 压缩数据
     * 
     * @param data 原始数据
     * @return 压缩结果
     */
    @PostMapping("/compress")
    public ResponseEntity<Map<String, Object>> compressData(@RequestBody byte[] data) {
        if (networkOptimizationService == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "网络优化服务未启用");
            return ResponseEntity.badRequest().body(error);
        }
        
        byte[] compressed = networkOptimizationService.compressData(data);
        
        Map<String, Object> result = new HashMap<>();
        result.put("originalSize", data.length);
        result.put("compressedSize", compressed.length);
        result.put("compressionRatio", (double) compressed.length / data.length);
        result.put("spaceSaved", data.length - compressed.length);
        result.put("compressedData", compressed);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 异步写入数据（测试）
     * 
     * @param data 写入数据
     * @return 写入结果
     */
    @PostMapping("/async/write")
    public ResponseEntity<CompletableFuture<Boolean>> asyncWrite(@RequestBody Map<String, Object> data) {
        if (asyncProcessingService == null) {
            return ResponseEntity.notFound().build();
        }
        
        String key = (String) data.get("key");
        byte[] value = ((String) data.get("value")).getBytes();
        
        CompletableFuture<Boolean> result = asyncProcessingService.writeAsync(key, value);
        return ResponseEntity.ok(result);
    }

    /**
     * 异步读取数据（测试）
     * 
     * @param key 键
     * @return 读取结果
     */
    @GetMapping("/async/read/{key}")
    public ResponseEntity<CompletableFuture<byte[]>> asyncRead(@PathVariable String key) {
        if (asyncProcessingService == null) {
            return ResponseEntity.notFound().build();
        }
        
        CompletableFuture<byte[]> result = asyncProcessingService.readAsync(key);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取性能优化建议
     * 
     * @return 优化建议
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getOptimizationRecommendations() {
        Map<String, Object> recommendations = new HashMap<>();
        
        // 基于当前性能指标生成建议
        recommendations.put("raftConfig", "建议调整Raft心跳间隔为300ms，选举超时为3000ms");
        recommendations.put("loadBalancer", "建议启用一致性哈希负载均衡策略");
        recommendations.put("network", "建议启用数据压缩和请求管道化");
        recommendations.put("async", "建议增加异步处理线程池大小到50");
        recommendations.put("batching", "建议启用批处理，批大小设置为100");
        recommendations.put("caching", "建议增加缓存预热策略");
        
        recommendations.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(recommendations);
    }
} 