package com.github.raftimpl.raft.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储操作响应DTO
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "存储操作响应")
public class StorageResponse {

    @Schema(description = "键", example = "user:1001")
    private String key;

    @Schema(description = "值", example = "{'name':'张三','age':25}")
    private String value;

    @Schema(description = "是否存在", example = "true")
    private Boolean exists;

    @Schema(description = "创建时间", example = "1641024000000")
    private Long createTime;

    @Schema(description = "更新时间", example = "1641024000000")
    private Long updateTime;

    @Schema(description = "过期时间", example = "1641027600000")
    private Long expireTime;
} 