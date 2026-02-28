package com.github.raftimpl.raft.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.util.Map;

/**
 * 对象存储响应DTO
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "对象存储响应")
public class ObjectStorageResponse {

    @Schema(description = "操作是否成功", example = "true")
    private Boolean success;

    @Schema(description = "响应消息", example = "文件上传成功")
    private String message;

    @Schema(description = "存储桶名称", example = "my-bucket")
    private String bucket;

    @Schema(description = "对象键名", example = "documents/file.pdf")
    private String objectKey;

    @Schema(description = "ETag标识", example = "d41d8cd98f00b204e9800998ecf8427e")
    private String etag;

    @Schema(description = "版本ID", example = "v1.0")
    private String versionId;

    @Schema(description = "文件大小（字节）", example = "1048576")
    private Long size;

    @Schema(description = "内容类型", example = "application/pdf")
    private String contentType;

    @Schema(description = "最后修改时间", example = "1641024000000")
    private Long lastModified;

    @Schema(description = "创建时间", example = "1641024000000")
    private Long createdTime;

    @Schema(description = "过期时间", example = "1641110400000")
    private Long expirationTime;

    @Schema(description = "存储类型", example = "STANDARD")
    private String storageClass;

    @Schema(description = "自定义元数据")
    private Map<String, String> metadata;

    @Schema(description = "预签名URL", example = "https://example.com/presigned-url")
    private String presignedUrl;

    @Schema(description = "下载URL", example = "https://example.com/download-url")
    private String downloadUrl;

    @Schema(description = "对象数据流（内部使用，不序列化）")
    private transient InputStream dataStream;

    @Schema(description = "分片上传响应")
    private MultipartUploadResponse multipartUpload;

    @Schema(description = "复制操作响应")
    private CopyResponse copyResponse;

    /**
     * 分片上传响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "分片上传响应")
    public static class MultipartUploadResponse {
        @Schema(description = "上传ID")
        private String uploadId;

        @Schema(description = "分片ETag")
        private String partETag;

        @Schema(description = "分片号", example = "1")
        private Integer partNumber;

        @Schema(description = "上传状态", example = "COMPLETED")
        private String status;

        @Schema(description = "已上传分片数", example = "5")
        private Integer uploadedParts;

        @Schema(description = "分片总数", example = "10")
        private Integer totalParts;
    }

    /**
     * 复制操作响应
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "复制操作响应")
    public static class CopyResponse {
        @Schema(description = "源存储桶", example = "source-bucket")
        private String sourceBucket;

        @Schema(description = "源对象键", example = "source/file.pdf")
        private String sourceKey;

        @Schema(description = "目标存储桶", example = "dest-bucket")
        private String destBucket;

        @Schema(description = "目标对象键", example = "dest/file.pdf")
        private String destKey;

        @Schema(description = "复制时间", example = "1641024000000")
        private Long copyTime;
    }

    /**
     * 创建成功响应
     */
    public static ObjectStorageResponse success(String message) {
        return ObjectStorageResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * 创建成功响应（带数据）
     */
    public static ObjectStorageResponse success(String message, String bucket, String objectKey, String etag) {
        return ObjectStorageResponse.builder()
                .success(true)
                .message(message)
                .bucket(bucket)
                .objectKey(objectKey)
                .etag(etag)
                .createdTime(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建失败响应
     */
    public static ObjectStorageResponse failure(String message) {
        return ObjectStorageResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
} 