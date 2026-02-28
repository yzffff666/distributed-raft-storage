package com.github.raftimpl.raft.api.service;

import com.github.raftimpl.raft.api.dto.StorageRequest;
import com.github.raftimpl.raft.api.dto.StorageResponse;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 存储服务接口
 * 
 */
public interface StorageService {

    /**
     * 存储键值对
     * 
     * @param key 键
     * @param value 值
     * @param ttl 过期时间(秒)
     * @return 是否成功
     */
    boolean set(String key, String value, Long ttl);

    /**
     * 根据键获取值
     * 
     * @param key 键
     * @return 存储响应
     */
    StorageResponse get(String key);

    /**
     * 删除键值对
     * 
     * @param key 键
     * @return 是否成功
     */
    boolean delete(String key);

    /**
     * 检查键是否存在
     * 
     * @param key 键
     * @return 是否存在
     */
    boolean exists(String key);

    /**
     * 获取键列表
     * 
     * @param prefix 键前缀过滤
     * @param limit 分页大小
     * @param offset 分页偏移
     * @return 键列表
     */
    List<String> keys(String prefix, int limit, int offset);

    /**
     * 批量存储键值对
     * 
     * @param requests 存储请求列表
     * @return 操作结果映射
     */
    Map<String, Boolean> batchSet(List<StorageRequest> requests);

    /**
     * 批量获取键值对
     * 
     * @param keys 键列表
     * @return 存储响应映射
     */
    Map<String, StorageResponse> batchGet(List<String> keys);

    /**
     * 上传文件
     * 
     * @param file 文件
     * @param key 自定义键名
     * @return 存储响应
     */
    StorageResponse uploadFile(MultipartFile file, String key);

    /**
     * 下载文件
     * 
     * @param key 文件键
     * @param response HTTP响应
     */
    void downloadFile(String key, HttpServletResponse response);

    /**
     * 获取存储统计信息
     * 
     * @return 统计信息
     */
    Map<String, Object> getStats();
} 