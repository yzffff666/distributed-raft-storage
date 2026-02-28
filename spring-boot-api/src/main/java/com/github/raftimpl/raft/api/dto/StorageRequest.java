package com.github.raftimpl.raft.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 存储操作请求DTO
 * 
 */
@Data
@Schema(description = "存储操作请求")
public class StorageRequest {

    @Schema(description = "键", required = true, example = "user:1001")
    @NotBlank(message = "键不能为空")
    @Size(max = 255, message = "键长度不能超过255个字符")
    private String key;

    @Schema(description = "值", example = "{'name':'张三','age':25}")
    @Size(max = 10485760, message = "值长度不能超过10MB")
    private String value;

    @Schema(description = "过期时间(秒)", example = "3600")
    private Long ttl;
} 