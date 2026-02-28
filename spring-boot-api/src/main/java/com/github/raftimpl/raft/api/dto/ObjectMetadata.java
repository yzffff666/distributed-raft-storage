package com.github.raftimpl.raft.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 对象元数据DTO
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "对象元数据")
public class ObjectMetadata {

    @Schema(description = "存储桶名称", example = "my-bucket")
    private String bucket;

    @Schema(description = "对象键名", example = "documents/file.pdf")
    private String objectKey;

    @Schema(description = "文件大小（字节）", example = "1048576")
    private Long size;

    @Schema(description = "内容类型", example = "application/pdf")
    private String contentType;

    @Schema(description = "ETag标识", example = "d41d8cd98f00b204e9800998ecf8427e")
    private String etag;

    @Schema(description = "最后修改时间", example = "1641024000000")
    private Long lastModified;

    @Schema(description = "创建时间", example = "1641024000000")
    private Long createdTime;

    @Schema(description = "存储类型", example = "STANDARD")
    private String storageClass;

    @Schema(description = "自定义元数据")
    private Map<String, String> userMetadata;

    @Schema(description = "系统元数据")
    private Map<String, String> systemMetadata;

    @Schema(description = "版本ID", example = "v1.0")
    private String versionId;

    @Schema(description = "是否已加密", example = "true")
    private Boolean encrypted;

    @Schema(description = "加密算法", example = "AES256")
    private String encryptionAlgorithm;

    @Schema(description = "过期时间", example = "1641110400000")
    private Long expirationTime;

    @Schema(description = "访问次数", example = "10")
    private Long accessCount;

    @Schema(description = "最后访问时间", example = "1641024000000")
    private Long lastAccessTime;

    @Schema(description = "文件路径（本地存储）")
    private String filePath;

    @Schema(description = "分片信息")
    private MultipartInfo multipartInfo;

    /**
     * 分片上传信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "分片上传信息")
    public static class MultipartInfo {
        @Schema(description = "上传ID")
        private String uploadId;

        @Schema(description = "分片总数")
        private Integer totalParts;

        @Schema(description = "已上传分片数")
        private Integer uploadedParts;

        @Schema(description = "分片大小")
        private Long partSize;

        @Schema(description = "上传状态", example = "IN_PROGRESS")
        private String status;
    }
} 