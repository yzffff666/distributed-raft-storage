package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.dto.StorageRequest;
import com.github.raftimpl.raft.api.dto.StorageResponse;
import com.github.raftimpl.raft.api.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 分布式存储RESTful API控制器
 * 
 */
@RestController
@RequestMapping("/storage")
@Tag(name = "存储管理", description = "分布式存储相关API")
@RequiredArgsConstructor
@Slf4j
@Validated
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/set")
    @Operation(summary = "存储键值对")
    public ApiResponse<Boolean> set(@Valid @RequestBody StorageRequest request) {
        log.info("存储请求: key={}, valueLength={}", request.getKey(), 
                request.getValue() != null ? request.getValue().length() : 0);
        
        boolean success = storageService.set(request.getKey(), request.getValue(), request.getTtl());
        return ApiResponse.success(success);
    }

    @GetMapping("/get/{key}")
    @Operation(summary = "根据键获取值")
    public ApiResponse<StorageResponse> get(
            @Parameter(description = "键", required = true) @PathVariable @NotBlank String key) {
        log.info("获取请求: key={}", key);
        
        StorageResponse response = storageService.get(key);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/delete/{key}")
    @Operation(summary = "删除键值对")
    public ApiResponse<Boolean> delete(
            @Parameter(description = "键", required = true) @PathVariable @NotBlank String key) {
        log.info("删除请求: key={}", key);
        
        boolean success = storageService.delete(key);
        return ApiResponse.success(success);
    }

    @GetMapping("/exists/{key}")
    @Operation(summary = "检查键是否存在")
    public ApiResponse<Boolean> exists(
            @Parameter(description = "键", required = true) @PathVariable @NotBlank String key) {
        log.info("存在性检查请求: key={}", key);
        
        boolean exists = storageService.exists(key);
        return ApiResponse.success(exists);
    }

    @GetMapping("/keys")
    @Operation(summary = "获取所有键列表")
    public ApiResponse<List<String>> keys(
            @Parameter(description = "键前缀过滤") @RequestParam(required = false) String prefix,
            @Parameter(description = "分页大小") @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "分页偏移") @RequestParam(defaultValue = "0") int offset) {
        log.info("获取键列表请求: prefix={}, limit={}, offset={}", prefix, limit, offset);
        
        List<String> keys = storageService.keys(prefix, limit, offset);
        return ApiResponse.success(keys);
    }

    @PostMapping("/batch/set")
    @Operation(summary = "批量存储键值对")
    public ApiResponse<Map<String, Boolean>> batchSet(@Valid @RequestBody List<StorageRequest> requests) {
        log.info("批量存储请求: count={}", requests.size());
        
        Map<String, Boolean> results = storageService.batchSet(requests);
        return ApiResponse.success(results);
    }

    @PostMapping("/batch/get")
    @Operation(summary = "批量获取键值对")
    public ApiResponse<Map<String, StorageResponse>> batchGet(@RequestBody List<String> keys) {
        log.info("批量获取请求: count={}", keys.size());
        
        Map<String, StorageResponse> results = storageService.batchGet(keys);
        return ApiResponse.success(results);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件")
    public ApiResponse<StorageResponse> uploadFile(
            @Parameter(description = "文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "自定义键名") @RequestParam(required = false) String key) {
        log.info("文件上传请求: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        
        StorageResponse response = storageService.uploadFile(file, key);
        return ApiResponse.success("文件上传成功", response);
    }

    @GetMapping("/download/{key}")
    @Operation(summary = "下载文件")
    public void downloadFile(
            @Parameter(description = "文件键", required = true) @PathVariable @NotBlank String key,
            javax.servlet.http.HttpServletResponse response) {
        log.info("文件下载请求: key={}", key);
        
        storageService.downloadFile(key, response);
    }

    @GetMapping("/stats")
    @Operation(summary = "获取存储统计信息")
    public ApiResponse<Map<String, Object>> getStats() {
        log.info("获取存储统计信息请求");
        
        Map<String, Object> stats = storageService.getStats();
        return ApiResponse.success(stats);
    }
} 