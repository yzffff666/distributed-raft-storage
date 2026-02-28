package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.dto.ObjectMetadata;
import com.github.raftimpl.raft.api.dto.ObjectStorageRequest;
import com.github.raftimpl.raft.api.dto.ObjectStorageResponse;
import com.github.raftimpl.raft.api.service.ObjectStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 对象存储RESTful API控制器
 * 
 */
@RestController
@RequestMapping("/object-storage")
@Tag(name = "对象存储管理", description = "企业级对象存储相关API")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ObjectStorageController {

    private final ObjectStorageService objectStorageService;

    // 存储桶管理

    @PostMapping("/buckets/{bucket}")
    @Operation(summary = "创建存储桶")
    public ApiResponse<Boolean> createBucket(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket) {
        log.info("创建存储桶请求: bucket={}", bucket);
        
        boolean success = objectStorageService.createBucket(bucket);
        return success ? 
                ApiResponse.success("存储桶创建成功", success) : 
                ApiResponse.error("存储桶创建失败");
    }

    @DeleteMapping("/buckets/{bucket}")
    @Operation(summary = "删除存储桶")
    public ApiResponse<Boolean> deleteBucket(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket) {
        log.info("删除存储桶请求: bucket={}", bucket);
        
        boolean success = objectStorageService.deleteBucket(bucket);
        return success ? 
                ApiResponse.success("存储桶删除成功", success) : 
                ApiResponse.error("存储桶删除失败");
    }

    @GetMapping("/buckets")
    @Operation(summary = "列出所有存储桶")
    public ApiResponse<List<String>> listBuckets() {
        log.info("列出存储桶请求");
        
        List<String> buckets = objectStorageService.listBuckets();
        return ApiResponse.success(buckets);
    }

    @GetMapping("/buckets/{bucket}/exists")
    @Operation(summary = "检查存储桶是否存在")
    public ApiResponse<Boolean> bucketExists(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket) {
        log.info("检查存储桶是否存在请求: bucket={}", bucket);
        
        boolean exists = objectStorageService.bucketExists(bucket);
        return ApiResponse.success(exists);
    }

    // 对象管理

    @PostMapping(value = "/buckets/{bucket}/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传对象")
    public ApiResponse<ObjectStorageResponse> putObject(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @RequestParam @NotBlank String objectKey,
            @Parameter(description = "文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "自定义元数据") @RequestParam(required = false) Map<String, String> metadata) {
        log.info("上传对象请求: bucket={}, objectKey={}, filename={}, size={}", 
                bucket, objectKey, file.getOriginalFilename(), file.getSize());
        
        ObjectStorageResponse response = objectStorageService.putObject(file, bucket, objectKey, metadata);
        return response.getSuccess() ? 
                ApiResponse.success("对象上传成功", response) : 
                ApiResponse.error(response.getMessage());
    }

    @GetMapping("/buckets/{bucket}/objects/{objectKey}")
    @Operation(summary = "获取对象")
    public ApiResponse<ObjectStorageResponse> getObject(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        log.info("获取对象请求: bucket={}, objectKey={}", bucket, objectKey);
        
        ObjectStorageResponse response = objectStorageService.getObject(bucket, objectKey);
        return response.getSuccess() ? 
                ApiResponse.success("对象获取成功", response) : 
                ApiResponse.error(response.getMessage());
    }

    @GetMapping("/buckets/{bucket}/objects/{objectKey}/download")
    @Operation(summary = "下载对象")
    public void downloadObject(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            HttpServletResponse response) {
        log.info("下载对象请求: bucket={}, objectKey={}", bucket, objectKey);
        
        objectStorageService.downloadObject(bucket, objectKey, response);
    }

    @DeleteMapping("/buckets/{bucket}/objects/{objectKey}")
    @Operation(summary = "删除对象")
    public ApiResponse<Boolean> deleteObject(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        log.info("删除对象请求: bucket={}, objectKey={}", bucket, objectKey);
        
        boolean success = objectStorageService.deleteObject(bucket, objectKey);
        return success ? 
                ApiResponse.success("对象删除成功", success) : 
                ApiResponse.error("对象删除失败");
    }

    @PostMapping("/buckets/{bucket}/objects/batch-delete")
    @Operation(summary = "批量删除对象")
    public ApiResponse<Map<String, Boolean>> deleteObjects(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @RequestBody List<String> objectKeys) {
        log.info("批量删除对象请求: bucket={}, count={}", bucket, objectKeys.size());
        
        Map<String, Boolean> results = objectStorageService.deleteObjects(bucket, objectKeys);
        return ApiResponse.success(results);
    }

    @GetMapping("/buckets/{bucket}/objects/{objectKey}/exists")
    @Operation(summary = "检查对象是否存在")
    public ApiResponse<Boolean> objectExists(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        log.info("检查对象是否存在请求: bucket={}, objectKey={}", bucket, objectKey);
        
        boolean exists = objectStorageService.objectExists(bucket, objectKey);
        return ApiResponse.success(exists);
    }

    @GetMapping("/buckets/{bucket}/objects/{objectKey}/metadata")
    @Operation(summary = "获取对象元数据")
    public ApiResponse<ObjectMetadata> getObjectMetadata(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey) {
        log.info("获取对象元数据请求: bucket={}, objectKey={}", bucket, objectKey);
        
        ObjectMetadata metadata = objectStorageService.getObjectMetadata(bucket, objectKey);
        return metadata != null ? 
                ApiResponse.success(metadata) : 
                ApiResponse.error("对象不存在");
    }

    @GetMapping("/buckets/{bucket}/objects")
    @Operation(summary = "列出存储桶中的对象")
    public ApiResponse<List<ObjectMetadata>> listObjects(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键前缀过滤") @RequestParam(required = false) String prefix,
            @Parameter(description = "最大返回数量") @RequestParam(defaultValue = "100") int maxKeys,
            @Parameter(description = "分页标记") @RequestParam(required = false) String marker) {
        log.info("列出对象请求: bucket={}, prefix={}, maxKeys={}, marker={}", bucket, prefix, maxKeys, marker);
        
        List<ObjectMetadata> objects = objectStorageService.listObjects(bucket, prefix, maxKeys, marker);
        return ApiResponse.success(objects);
    }

    // 对象复制

    @PostMapping("/buckets/{bucket}/objects/{objectKey}/copy")
    @Operation(summary = "复制对象")
    public ApiResponse<ObjectStorageResponse> copyObject(
            @Parameter(description = "源存储桶", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "源对象键", required = true) @PathVariable @NotBlank String objectKey,
            @Valid @RequestBody ObjectStorageRequest request) {
        log.info("复制对象请求: {}:{} -> {}:{}", bucket, objectKey, request.getBucket(), request.getObjectKey());
        
        ObjectStorageResponse response = objectStorageService.copyObject(
                bucket, objectKey, request.getBucket(), request.getObjectKey(), request.getMetadata());
        return response.getSuccess() ? 
                ApiResponse.success("对象复制成功", response) : 
                ApiResponse.error(response.getMessage());
    }

    // 预签名URL

    @GetMapping("/buckets/{bucket}/objects/{objectKey}/presigned-url")
    @Operation(summary = "生成预签名URL")
    public ApiResponse<String> generatePresignedUrl(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @Parameter(description = "过期时间（秒）") @RequestParam(defaultValue = "3600") int expiration,
            @Parameter(description = "HTTP方法") @RequestParam(defaultValue = "GET") String method) {
        log.info("生成预签名URL请求: bucket={}, objectKey={}, expiration={}, method={}", 
                bucket, objectKey, expiration, method);
        
        String presignedUrl = objectStorageService.generatePresignedUrl(bucket, objectKey, expiration, method);
        return presignedUrl != null ? 
                ApiResponse.success(presignedUrl) : 
                ApiResponse.error("生成预签名URL失败");
    }

    // 分片上传

    @PostMapping("/buckets/{bucket}/objects/{objectKey}/multipart/initiate")
    @Operation(summary = "初始化分片上传")
    public ApiResponse<String> initiateMultipartUpload(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Map<String, String> metadata) {
        log.info("初始化分片上传请求: bucket={}, objectKey={}, contentType={}", bucket, objectKey, contentType);
        
        String uploadId = objectStorageService.initiateMultipartUpload(bucket, objectKey, contentType, metadata);
        return uploadId != null ? 
                ApiResponse.success("分片上传初始化成功", uploadId) : 
                ApiResponse.error("分片上传初始化失败");
    }

    @PostMapping("/buckets/{bucket}/objects/{objectKey}/multipart/{uploadId}/complete")
    @Operation(summary = "完成分片上传")
    public ApiResponse<ObjectStorageResponse> completeMultipartUpload(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @Parameter(description = "上传ID", required = true) @PathVariable @NotBlank String uploadId,
            @RequestBody List<String> partETags) {
        log.info("完成分片上传请求: bucket={}, objectKey={}, uploadId={}, parts={}", 
                bucket, objectKey, uploadId, partETags.size());
        
        ObjectStorageResponse response = objectStorageService.completeMultipartUpload(bucket, objectKey, uploadId, partETags);
        return response.getSuccess() ? 
                ApiResponse.success("分片上传完成", response) : 
                ApiResponse.error(response.getMessage());
    }

    @DeleteMapping("/buckets/{bucket}/objects/{objectKey}/multipart/{uploadId}")
    @Operation(summary = "取消分片上传")
    public ApiResponse<Boolean> abortMultipartUpload(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @Parameter(description = "上传ID", required = true) @PathVariable @NotBlank String uploadId) {
        log.info("取消分片上传请求: bucket={}, objectKey={}, uploadId={}", bucket, objectKey, uploadId);
        
        boolean success = objectStorageService.abortMultipartUpload(bucket, objectKey, uploadId);
        return success ? 
                ApiResponse.success("分片上传取消成功", success) : 
                ApiResponse.error("分片上传取消失败");
    }

    // 生命周期管理

    @PutMapping("/buckets/{bucket}/objects/{objectKey}/storage-class")
    @Operation(summary = "设置对象存储类型")
    public ApiResponse<Boolean> setObjectStorageClass(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @Parameter(description = "存储类型", required = true) @RequestParam @NotBlank String storageClass) {
        log.info("设置对象存储类型请求: bucket={}, objectKey={}, storageClass={}", bucket, objectKey, storageClass);
        
        boolean success = objectStorageService.setObjectStorageClass(bucket, objectKey, storageClass);
        return success ? 
                ApiResponse.success("设置存储类型成功", success) : 
                ApiResponse.error("设置存储类型失败");
    }

    @PostMapping("/buckets/{bucket}/objects/{objectKey}/restore")
    @Operation(summary = "恢复归档对象")
    public ApiResponse<Boolean> restoreObject(
            @Parameter(description = "存储桶名称", required = true) @PathVariable @NotBlank String bucket,
            @Parameter(description = "对象键名", required = true) @PathVariable @NotBlank String objectKey,
            @Parameter(description = "恢复天数", required = true) @RequestParam int days) {
        log.info("恢复归档对象请求: bucket={}, objectKey={}, days={}", bucket, objectKey, days);
        
        boolean success = objectStorageService.restoreObject(bucket, objectKey, days);
        return success ? 
                ApiResponse.success("恢复归档对象成功", success) : 
                ApiResponse.error("恢复归档对象失败");
    }

    // 统计信息

    @GetMapping("/stats")
    @Operation(summary = "获取存储统计信息")
    public ApiResponse<Map<String, Object>> getStorageStats() {
        log.info("获取存储统计信息请求");
        
        Map<String, Object> stats = objectStorageService.getStorageStats();
        return ApiResponse.success(stats);
    }
} 