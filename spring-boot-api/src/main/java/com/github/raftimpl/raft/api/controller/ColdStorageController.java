package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.service.ColdStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * 冷存储管理控制器
 * 提供数据分层存储和生命周期管理的API接口
 * 
 */
@RestController
@RequestMapping("/cold-storage")
@Tag(name = "冷存储管理", description = "数据分层存储和生命周期管理相关API")
@RequiredArgsConstructor
@Slf4j
public class ColdStorageController {

    private final ColdStorageService coldStorageService;

    @PostMapping("/objects/{objectKey}")
    @Operation(summary = "存储对象到冷存储")
    public ApiResponse<Boolean> storeObject(
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @Parameter(description = "文件数据", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "存储层级") @RequestParam(defaultValue = "HOT") String tier) {
        
        log.info("冷存储对象存储请求: key={}, tier={}, size={}", objectKey, tier, file.getSize());
        
        try {
            byte[] data = file.getBytes();
            boolean success = coldStorageService.storeObject(objectKey, data, tier);
            
            return ApiResponse.success("对象存储" + (success ? "成功" : "失败"), success);
            
        } catch (IOException e) {
            log.error("对象存储失败: key={}, tier={}", objectKey, tier, e);
            return ApiResponse.error("对象存储失败: " + e.getMessage());
        }
    }

    @GetMapping("/objects/{objectKey}")
    @Operation(summary = "从冷存储获取对象")
    public ApiResponse<String> getObject(
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        
        log.info("冷存储对象获取请求: key={}", objectKey);
        
        try {
            byte[] data = coldStorageService.getObject(objectKey);
            if (data == null) {
                return ApiResponse.error("对象不存在: " + objectKey);
            }
            
            // 返回Base64编码的数据
            String encodedData = Base64.getEncoder().encodeToString(data);
            return ApiResponse.success("对象获取成功", encodedData);
            
        } catch (Exception e) {
            log.error("对象获取失败: key={}", objectKey, e);
            return ApiResponse.error("对象获取失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/objects/{objectKey}")
    @Operation(summary = "删除冷存储对象")
    public ApiResponse<Boolean> deleteObject(
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        
        log.info("冷存储对象删除请求: key={}", objectKey);
        
        try {
            boolean success = coldStorageService.deleteObject(objectKey);
            return ApiResponse.success("对象删除" + (success ? "成功" : "失败"), success);
            
        } catch (Exception e) {
            log.error("对象删除失败: key={}", objectKey, e);
            return ApiResponse.error("对象删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/objects/{objectKey}/exists")
    @Operation(summary = "检查对象是否存在")
    public ApiResponse<Boolean> objectExists(
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        
        log.info("对象存在性检查请求: key={}", objectKey);
        
        try {
            boolean exists = coldStorageService.objectExists(objectKey);
            return ApiResponse.success("对象存在性检查完成", exists);
            
        } catch (Exception e) {
            log.error("对象存在性检查失败: key={}", objectKey, e);
            return ApiResponse.error("对象存在性检查失败: " + e.getMessage());
        }
    }

    @GetMapping("/objects/{objectKey}/tier")
    @Operation(summary = "获取对象所在的存储层级")
    public ApiResponse<String> getObjectTier(
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        
        log.info("对象存储层级查询请求: key={}", objectKey);
        
        try {
            String tier = coldStorageService.getObjectTier(objectKey);
            if (tier == null) {
                return ApiResponse.error("对象不存在: " + objectKey);
            }
            
            return ApiResponse.success("对象存储层级获取成功", tier);
            
        } catch (Exception e) {
            log.error("对象存储层级查询失败: key={}", objectKey, e);
            return ApiResponse.error("对象存储层级查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/objects/{objectKey}/migrate")
    @Operation(summary = "迁移对象到指定存储层级")
    public ApiResponse<Boolean> migrateObject(
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @Parameter(description = "目标存储层级", required = true) @RequestParam @NotBlank String targetTier) {
        
        log.info("对象迁移请求: key={}, targetTier={}", objectKey, targetTier);
        
        try {
            boolean success = coldStorageService.migrateObject(objectKey, targetTier);
            return ApiResponse.success("对象迁移" + (success ? "成功" : "失败"), success);
            
        } catch (Exception e) {
            log.error("对象迁移失败: key={}, targetTier={}", objectKey, targetTier, e);
            return ApiResponse.error("对象迁移失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "获取冷存储统计信息")
    public ApiResponse<Map<String, Object>> getStorageStats() {
        log.info("获取冷存储统计信息请求");
        
        try {
            Map<String, Object> stats = coldStorageService.getStorageStats();
            return ApiResponse.success("获取冷存储统计信息成功", stats);
            
        } catch (Exception e) {
            log.error("获取冷存储统计信息失败", e);
            return ApiResponse.error("获取冷存储统计信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/access-stats")
    @Operation(summary = "获取所有数据访问统计")
    public ApiResponse<Map<String, ColdStorageService.DataAccessStats>> getAllAccessStats() {
        log.info("获取所有数据访问统计请求");
        
        try {
            Map<String, ColdStorageService.DataAccessStats> stats = coldStorageService.getAllDataAccessStats();
            return ApiResponse.success("获取数据访问统计成功", stats);
            
        } catch (Exception e) {
            log.error("获取数据访问统计失败", e);
            return ApiResponse.error("获取数据访问统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/access-stats/{objectKey}")
    @Operation(summary = "获取指定对象的访问统计")
    public ApiResponse<ColdStorageService.DataAccessStats> getAccessStats(
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        
        log.info("获取对象访问统计请求: key={}", objectKey);
        
        try {
            ColdStorageService.DataAccessStats stats = coldStorageService.getDataAccessStats(objectKey);
            if (stats == null) {
                return ApiResponse.error("对象访问统计不存在: " + objectKey);
            }
            
            return ApiResponse.success("获取对象访问统计成功", stats);
            
        } catch (Exception e) {
            log.error("获取对象访问统计失败: key={}", objectKey, e);
            return ApiResponse.error("获取对象访问统计失败: " + e.getMessage());
        }
    }

    @PostMapping("/initialize")
    @Operation(summary = "初始化冷存储服务")
    public ApiResponse<Boolean> initializeService() {
        log.info("初始化冷存储服务请求");
        
        try {
            coldStorageService.initialize();
            return ApiResponse.success("冷存储服务初始化成功", true);
            
        } catch (Exception e) {
            log.error("冷存储服务初始化失败", e);
            return ApiResponse.error("冷存储服务初始化失败: " + e.getMessage());
        }
    }
} 