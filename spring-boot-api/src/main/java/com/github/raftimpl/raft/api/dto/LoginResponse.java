package com.github.raftimpl.raft.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应DTO
 */
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "JWT访问令牌")
    private String accessToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "令牌过期时间（秒）")
    private Long expiresIn;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户角色")
    private String role;

    public LoginResponse() {}

    public LoginResponse(String accessToken, Long expiresIn, String username, String role) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.username = username;
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
} 