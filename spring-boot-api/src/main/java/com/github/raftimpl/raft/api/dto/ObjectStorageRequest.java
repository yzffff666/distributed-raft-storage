package com.github.raftimpl.raft.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 对象存储请求DTO
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "对象存储请求")
public class ObjectStorageRequest {

    @Schema(description = "存储桶名称", required = true, example = "my-bucket")
    @NotBlank(message = "存储桶名称不能为空")
    @Size(max = 63, message = "存储桶名称长度不能超过63个字符")
    private String bucket;

    @Schema(description = "对象键名", required = true, example = "documents/file.pdf")
    @NotBlank(message = "对象键名不能为空")
    @Size(max = 1024, message = "对象键名长度不能超过1024个字符")
    private String objectKey;

    @Schema(description = "内容类型", example = "application/pdf")
    private String contentType;

    @Schema(description = "存储类型", example = "STANDARD")
    private String storageClass;

    @Schema(description = "自定义元数据")
    private Map<String, String> metadata;

    @Schema(description = "过期时间（秒）", example = "86400")
    private Long expirationTime;

    @Schema(description = "是否启用加密", example = "true")
    private Boolean encrypted;

    @Schema(description = "加密算法", example = "AES256")
    private String encryptionAlgorithm;

    @Schema(description = "访问控制", example = "private")
    private String acl;

    @Schema(description = "缓存控制", example = "max-age=3600")
    private String cacheControl;

    @Schema(description = "内容编码", example = "gzip")
    private String contentEncoding;

    @Schema(description = "内容语言", example = "zh-CN")
    private String contentLanguage;

    @Schema(description = "内容处置", example = "attachment; filename=file.pdf")
    private String contentDisposition;

    @Schema(description = "分片上传请求")
    private MultipartUploadRequest multipartUpload;

    /**
     * 分片上传请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "分片上传请求")
    public static class MultipartUploadRequest {
        @Schema(description = "上传ID")
        private String uploadId;

        @Schema(description = "分片号", example = "1")
        private Integer partNumber;

        @Schema(description = "分片大小", example = "5242880")
        private Long partSize;

        @Schema(description = "分片ETag列表")
        private java.util.List<String> partETags;

        @Schema(description = "操作类型", example = "INITIATE")
        private String operation; // INITIATE, UPLOAD_PART, COMPLETE, ABORT
    }
} 