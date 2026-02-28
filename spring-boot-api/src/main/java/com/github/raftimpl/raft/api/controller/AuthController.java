package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.dto.LoginRequest;
import com.github.raftimpl.raft.api.dto.LoginResponse;
import com.github.raftimpl.raft.api.entity.User;
import com.github.raftimpl.raft.api.service.UserService;
import com.github.raftimpl.raft.api.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "用户认证相关API")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录获取JWT令牌")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // 验证用户凭证
            if (!userService.validatePassword(loginRequest.getUsername(), loginRequest.getPassword())) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户名或密码错误"));
            }

            // 获取用户信息
            User user = userService.findByUsername(loginRequest.getUsername());
            if (user == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户不存在"));
            }

            // 生成JWT令牌
            String token = jwtUtil.generateToken(user);
            
            LoginResponse loginResponse = new LoginResponse(
                token, 
                jwtExpiration / 1000, // 转换为秒
                user.getUsername(),
                user.getRole()
            );

            return ResponseEntity.ok(ApiResponse.success("登录成功", loginResponse));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("登录失败: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用有效的JWT令牌获取新的令牌")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("无效的Authorization头"));
            }

            String token = authHeader.substring(7);
            
            if (!jwtUtil.isTokenValid(token)) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("令牌无效或已过期"));
            }

            String username = jwtUtil.getUsernameFromToken(token);
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户不存在"));
            }

            // 生成新的令牌
            String newToken = jwtUtil.generateToken(user);
            
            LoginResponse response = new LoginResponse(
                newToken,
                jwtExpiration / 1000,
                user.getUsername(),
                user.getRole()
            );

            return ResponseEntity.ok(ApiResponse.success("令牌刷新成功", response));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("令牌刷新失败: " + e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的信息")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户未认证"));
            }

            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            if (user == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户不存在"));
            }

            // 不返回密码信息
            User safeUser = new User(user.getUsername(), "", user.getRole());
            
            return ResponseEntity.ok(ApiResponse.success("获取用户信息成功", safeUser));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取用户信息失败: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出（客户端需要删除本地令牌）")
    public ResponseEntity<ApiResponse<String>> logout() {
        // JWT是无状态的，登出主要由客户端处理（删除本地存储的令牌）
        // 服务端可以维护一个黑名单来处理已登出的令牌，这里简化处理
        return ResponseEntity.ok(ApiResponse.success(null, "登出成功，请删除本地令牌"));
    }

    @PostMapping("/register")
    @Operation(summary = "注册新用户", description = "注册新用户（需要管理员权限）")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "USER") String role) {
        try {
            // 检查用户是否已存在
            if (userService.findByUsername(username) != null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用户名已存在"));
            }

            // 创建新用户
            User newUser = userService.createUser(username, password, role);
            
            // 不返回密码信息
            User safeUser = new User(newUser.getUsername(), "", newUser.getRole());
            
            return ResponseEntity.ok(ApiResponse.success("用户注册成功", safeUser));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("用户注册失败: " + e.getMessage()));
        }
    }
} 