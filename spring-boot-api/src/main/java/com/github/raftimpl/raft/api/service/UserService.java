package com.github.raftimpl.raft.api.service;

import com.github.raftimpl.raft.api.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 用户服务接口
 */
public interface UserService extends UserDetailsService {
    
    /**
     * 根据用户名查找用户
     */
    User findByUsername(String username);
    
    /**
     * 创建新用户
     */
    User createUser(String username, String password, String role);
    
    /**
     * 验证用户密码
     */
    boolean validatePassword(String username, String password);
} 