package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.service.ClusterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Raft集群管理RESTful API控制器
 * 
 */
@RestController
@RequestMapping("/cluster")
@Tag(name = "集群管理", description = "分布式集群管理相关API")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ClusterController {

    private final ClusterService clusterService;

    @GetMapping("/info")
    @Operation(summary = "获取集群信息")
    public ApiResponse<Map<String, Object>> getClusterInfo() {
        log.info("获取集群信息请求");
        
        Map<String, Object> clusterInfo = clusterService.getClusterInfo();
        return ApiResponse.success(clusterInfo);
    }

    @GetMapping("/leader")
    @Operation(summary = "获取Leader节点信息")
    public ApiResponse<Map<String, Object>> getLeaderInfo() {
        log.info("获取Leader节点信息请求");
        
        Map<String, Object> leaderInfo = clusterService.getLeaderInfo();
        return ApiResponse.success(leaderInfo);
    }

    @GetMapping("/nodes")
    @Operation(summary = "获取所有节点信息")
    public ApiResponse<Map<String, Object>> getNodesInfo() {
        log.info("获取所有节点信息请求");
        
        Map<String, Object> nodesInfo = clusterService.getNodesInfo();
        return ApiResponse.success(nodesInfo);
    }

    @PostMapping("/nodes/add")
    @Operation(summary = "添加节点")
    public ApiResponse<Boolean> addNode(
            @Parameter(description = "节点ID", required = true) @RequestParam @NotNull Integer nodeId,
            @Parameter(description = "节点主机", required = true) @RequestParam @NotBlank String host,
            @Parameter(description = "节点端口", required = true) @RequestParam @NotNull Integer port) {
        log.info("添加节点请求: nodeId={}, host={}, port={}", nodeId, host, port);
        
        boolean success = clusterService.addNode(nodeId, host, port);
        return ApiResponse.success(success);
    }

    @DeleteMapping("/nodes/{nodeId}")
    @Operation(summary = "移除节点")
    public ApiResponse<Boolean> removeNode(
            @Parameter(description = "节点ID", required = true) @PathVariable @NotNull Integer nodeId) {
        log.info("移除节点请求: nodeId={}", nodeId);
        
        boolean success = clusterService.removeNode(nodeId);
        return ApiResponse.success(success);
    }

    @GetMapping("/status")
    @Operation(summary = "获取集群状态")
    public ApiResponse<Map<String, Object>> getClusterStatus() {
        log.info("获取集群状态请求");
        
        Map<String, Object> status = clusterService.getClusterStatus();
        return ApiResponse.success(status);
    }

    @GetMapping("/metrics")
    @Operation(summary = "获取集群指标")
    public ApiResponse<Map<String, Object>> getClusterMetrics() {
        log.info("获取集群指标请求");
        
        Map<String, Object> metrics = clusterService.getClusterMetrics();
        return ApiResponse.success(metrics);
    }

    @PostMapping("/transfer-leader")
    @Operation(summary = "转移Leader")
    public ApiResponse<Boolean> transferLeader(
            @Parameter(description = "目标节点ID", required = true) @RequestParam @NotNull Integer targetNodeId) {
        log.info("转移Leader请求: targetNodeId={}", targetNodeId);
        
        boolean success = clusterService.transferLeader(targetNodeId);
        return ApiResponse.success(success);
    }

    @PostMapping("/snapshot")
    @Operation(summary = "触发快照")
    public ApiResponse<Boolean> triggerSnapshot() {
        log.info("触发快照请求");
        
        boolean success = clusterService.triggerSnapshot();
        return ApiResponse.success(success);
    }

    @GetMapping("/logs")
    @Operation(summary = "获取日志信息")
    public ApiResponse<Map<String, Object>> getLogInfo(
            @Parameter(description = "起始索引") @RequestParam(defaultValue = "0") long startIndex,
            @Parameter(description = "结束索引") @RequestParam(defaultValue = "100") long endIndex) {
        log.info("获取日志信息请求: startIndex={}, endIndex={}", startIndex, endIndex);
        
        Map<String, Object> logInfo = clusterService.getLogInfo(startIndex, endIndex);
        return ApiResponse.success(logInfo);
    }
} 