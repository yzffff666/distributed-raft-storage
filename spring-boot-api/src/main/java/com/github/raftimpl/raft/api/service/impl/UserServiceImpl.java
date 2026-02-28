package com.github.raftimpl.raft.api.service.impl;

import com.github.raftimpl.raft.api.entity.User;
import com.github.raftimpl.raft.api.service.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 用户服务实现类
 * 使用内存存储模拟用户数据
 */
@Service
public class UserServiceImpl implements UserService {

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    public void initDefaultUsers() {
        // 创建默认管理员用户
        createUser("admin", "admin123", "ADMIN");
        // 创建默认普通用户
        createUser("user", "user123", "USER");
        // 创建只读用户
        createUser("readonly", "readonly123", "READONLY");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return user;
    }

    @Override
    public User findByUsername(String username) {
        return users.get(username);
    }

    @Override
    public User createUser(String username, String password, String role) {
        String encodedPassword = passwordEncoder.encode(password);
        User user = new User(username, encodedPassword, role);
        users.put(username, user);
        return user;
    }

    @Override
    public boolean validatePassword(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            return false;
        }
        return passwordEncoder.matches(password, user.getPassword());
    }

    /**
     * 获取所有用户（用于管理）
     */
    public Map<String, User> getAllUsers() {
        return new ConcurrentHashMap<>(users);
    }

    /**
     * 删除用户
     */
    public boolean deleteUser(String username) {
        return users.remove(username) != null;
    }

    /**
     * 更新用户密码
     */
    public boolean updatePassword(String username, String newPassword) {
        User user = findByUsername(username);
        if (user != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
            return true;
        }
        return false;
    }

    /**
     * 更新用户角色
     */
    public boolean updateRole(String username, String newRole) {
        User user = findByUsername(username);
        if (user != null) {
            user.setRole(newRole);
            return true;
        }
        return false;
    }
} 