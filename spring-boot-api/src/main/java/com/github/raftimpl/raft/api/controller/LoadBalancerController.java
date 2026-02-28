package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.NodeInfo;
import com.github.raftimpl.raft.api.service.LoadBalancerService;
import com.github.raftimpl.raft.api.service.impl.LoadBalancerServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负载均衡控制器
 * 提供负载均衡管理的REST API接口
 * 
 */
@RestController
@RequestMapping("/api/v1/loadbalancer")
public class LoadBalancerController {

    @Autowired
    private LoadBalancerService loadBalancerService;

    @Autowired
    private LoadBalancerServiceImpl loadBalancerServiceImpl;

    /**
     * 获取Leader节点
     * 
     * @return Leader节点信息
     */
    @GetMapping("/leader")
    public ResponseEntity<NodeInfo> getLeaderNode() {
        NodeInfo leader = loadBalancerService.getLeaderNode();
        if (leader != null) {
            return ResponseEntity.ok(leader);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取读节点（支持负载均衡策略）
     * 
     * @param strategy 负载均衡策略
     * @return 选中的读节点
     */
    @GetMapping("/read-node")
    public ResponseEntity<NodeInfo> getReadNode(
            @RequestParam(defaultValue = "ROUND_ROBIN") LoadBalancerService.LoadBalanceStrategy strategy) {
        NodeInfo readNode = loadBalancerService.getReadNode(strategy);
        if (readNode != null) {
            return ResponseEntity.ok(readNode);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取所有可用节点
     * 
     * @return 可用节点列表
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<NodeInfo>> getAvailableNodes() {
        List<NodeInfo> nodes = loadBalancerService.getAvailableNodes();
        return ResponseEntity.ok(nodes);
    }

    /**
     * 检查节点健康状态
     * 
     * @param nodeId 节点ID
     * @return 健康状态
     */
    @GetMapping("/nodes/{nodeId}/health")
    public ResponseEntity<Map<String, Object>> checkNodeHealth(@PathVariable String nodeId) {
        boolean healthy = loadBalancerService.isNodeHealthy(nodeId);
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeId);
        result.put("healthy", healthy);
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    /**
     * 更新节点健康状态
     * 
     * @param nodeId 节点ID
     * @param healthy 健康状态
     * @return 操作结果
     */
    @PutMapping("/nodes/{nodeId}/health")
    public ResponseEntity<Map<String, Object>> updateNodeHealth(
            @PathVariable String nodeId,
            @RequestParam boolean healthy) {
        loadBalancerService.updateNodeHealth(nodeId, healthy);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "节点健康状态已更新");
        result.put("nodeId", nodeId);
        result.put("healthy", healthy);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取节点负载信息
     * 
     * @param nodeId 节点ID
     * @return 节点负载信息
     */
    @GetMapping("/nodes/{nodeId}/load")
    public ResponseEntity<LoadBalancerService.NodeLoadInfo> getNodeLoad(@PathVariable String nodeId) {
        LoadBalancerService.NodeLoadInfo loadInfo = loadBalancerService.getNodeLoad(nodeId);
        return ResponseEntity.ok(loadInfo);
    }

    /**
     * 更新节点负载信息
     * 
     * @param nodeId 节点ID
     * @param loadData 负载数据
     * @return 操作结果
     */
    @PutMapping("/nodes/{nodeId}/load")
    public ResponseEntity<Map<String, Object>> updateNodeLoad(
            @PathVariable String nodeId,
            @RequestBody Map<String, Object> loadData) {
        
        double cpuUsage = ((Number) loadData.getOrDefault("cpuUsage", 0.0)).doubleValue();
        double memoryUsage = ((Number) loadData.getOrDefault("memoryUsage", 0.0)).doubleValue();
        int activeConnections = ((Number) loadData.getOrDefault("activeConnections", 0)).intValue();
        long responseTime = ((Number) loadData.getOrDefault("responseTime", 0L)).longValue();
        int qps = ((Number) loadData.getOrDefault("qps", 0)).intValue();
        
        loadBalancerServiceImpl.updateNodeLoadInfo(nodeId, cpuUsage, memoryUsage, 
                activeConnections, responseTime, qps);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "节点负载信息已更新");
        result.put("nodeId", nodeId);
        return ResponseEntity.ok(result);
    }

    /**
     * 注册节点
     * 
     * @param nodeInfo 节点信息
     * @return 操作结果
     */
    @PostMapping("/nodes")
    public ResponseEntity<Map<String, Object>> registerNode(@RequestBody NodeInfo nodeInfo) {
        loadBalancerService.registerNode(nodeInfo);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "节点注册成功");
        result.put("nodeId", nodeInfo.getNodeId());
        return ResponseEntity.ok(result);
    }

    /**
     * 注销节点
     * 
     * @param nodeId 节点ID
     * @return 操作结果
     */
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Map<String, Object>> unregisterNode(@PathVariable String nodeId) {
        loadBalancerService.unregisterNode(nodeId);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "节点注销成功");
        result.put("nodeId", nodeId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取负载均衡统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getLoadBalancerStats() {
        Map<String, Object> stats = loadBalancerServiceImpl.getLoadBalancerStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取支持的负载均衡策略
     * 
     * @return 策略列表
     */
    @GetMapping("/strategies")
    public ResponseEntity<LoadBalancerService.LoadBalanceStrategy[]> getSupportedStrategies() {
        return ResponseEntity.ok(LoadBalancerService.LoadBalanceStrategy.values());
    }
} 