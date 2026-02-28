package com.github.raftimpl.raft.api.service.impl;

import com.github.raftimpl.raft.api.dto.StorageRequest;
import com.github.raftimpl.raft.api.dto.StorageResponse;
import com.github.raftimpl.raft.api.service.CircuitBreakerService;
import com.github.raftimpl.raft.api.service.CacheService;
import com.github.raftimpl.raft.api.service.MonitoringService;
import com.github.raftimpl.raft.api.service.StorageService;
import com.github.raftimpl.raft.example.server.service.ExampleProto;
import com.github.raftimpl.raft.example.server.service.ExampleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 存储服务实现类
 * 连接现有的Raft存储引擎，提供RESTful API
 * 
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageServiceImpl implements StorageService {

    private final ExampleService exampleService;
    private final CircuitBreakerService circuitBreakerService;
    private final MonitoringService monitoringService;
    private final CacheService cacheService;
    
    // 内存元数据缓存（小数据量，快速访问）
    private final Map<String, StorageMetadata> metadataCache = new ConcurrentHashMap<>();

    @Override
    @CacheEvict(value = "storage", key = "#key")
    public boolean set(String key, String value, Long ttl) {
        Object sample = monitoringService.startStorageTimer();
        try {
            monitoringService.recordStorageOperation("set", "started");
            
            // 使用熔断器包装存储操作
            String result = circuitBreakerService.executeStorageOperation(() -> {
                ExampleProto.SetRequest.Builder requestBuilder = ExampleProto.SetRequest.newBuilder()
                        .setKey(key)
                        .setValue(value != null ? value : "");

                ExampleProto.SetRequest request = requestBuilder.build();
                ExampleProto.SetResponse response = exampleService.set(request);
                
                if (response.getSuccess()) {
                    // 存储元数据到内存缓存
                    StorageMetadata metadata = new StorageMetadata();
                    metadata.setKey(key);
                    metadata.setValue(value);
                    metadata.setCreateTime(System.currentTimeMillis());
                    metadata.setUpdateTime(System.currentTimeMillis());
                    
                    if (ttl != null && ttl > 0) {
                        metadata.setExpireTime(System.currentTimeMillis() + ttl * 1000);
                    }
                    
                    metadataCache.put(key, metadata);
                    
                    // 缓存热点数据到Redis
                    if (isHotData(key, value)) {
                        cacheHotData(key, value, ttl);
                    }
                    
                    log.info("成功存储键值对: key={}, valueLength={}, cached={}", 
                            key, value != null ? value.length() : 0, isHotData(key, value));
                    return "success";
                } else {
                    log.warn("存储键值对失败: key={}", key);
                    return "failed";
                }
            });
            
            return "success".equals(result);
        } catch (Exception e) {
            log.error("存储键值对异常: key={}", key, e);
            return false;
        } finally {
            monitoringService.recordStorageDuration(sample, "set");
        }
    }

    @Override
    @Cacheable(value = "storage", key = "#key")
    public StorageResponse get(String key) {
        Object sample = monitoringService.startStorageTimer();
        try {
            monitoringService.recordStorageOperation("get", "started");
            
            // 1. 优先从Redis缓存获取热点数据
            StorageResponse cachedResponse = getFromCache(key);
            if (cachedResponse != null) {
                monitoringService.recordCacheHit("storage");
                log.info("从缓存获取键值对: key={}, exists={}", key, cachedResponse.getExists());
                return cachedResponse;
            }
            
            // 2. 缓存未命中，从Raft存储获取
            ExampleProto.GetRequest request = ExampleProto.GetRequest.newBuilder()
                    .setKey(key)
                    .build();

            ExampleProto.GetResponse response = exampleService.get(request);
            StorageMetadata metadata = metadataCache.get(key);
            
            StorageResponse result = new StorageResponse();
            result.setKey(key);
            result.setExists(StringUtils.hasText(response.getValue()));

            if (StringUtils.hasText(response.getValue())) {
                // 检查是否过期
                if (metadata != null && metadata.getExpireTime() != null && 
                    metadata.getExpireTime() < System.currentTimeMillis()) {
                    // 数据已过期，删除
                    delete(key);
                    result.setExists(false);
                    monitoringService.recordCacheMiss("storage");
                } else {
                    result.setValue(response.getValue());
                    if (metadata != null) {
                        result.setCreateTime(metadata.getCreateTime());
                        result.setUpdateTime(metadata.getUpdateTime());
                        result.setExpireTime(metadata.getExpireTime());
                    }
                    
                    // 将热点数据缓存到Redis
                    if (isHotData(key, response.getValue())) {
                        cacheHotData(key, response.getValue(), null);
                    }
                    
                    monitoringService.recordCacheHit("storage");
                }
            } else {
                monitoringService.recordCacheMiss("storage");
            }

            log.info("获取键值对: key={}, exists={}, fromCache={}", key, result.getExists(), false);
            return result;
        } catch (Exception e) {
            log.error("获取键值对异常: key={}", key, e);
            monitoringService.recordCacheMiss("storage");
            StorageResponse errorResult = new StorageResponse();
            errorResult.setKey(key);
            errorResult.setExists(false);
            return errorResult;
        } finally {
            monitoringService.recordStorageDuration(sample, "get");
        }
    }

    @Override
    @CacheEvict(value = "storage", key = "#key")
    public boolean delete(String key) {
        Object sample = monitoringService.startStorageTimer();
        try {
            monitoringService.recordStorageOperation("delete", "started");
            
            // 由于现有的ExampleService没有删除接口，我们通过设置空值来模拟删除
            ExampleProto.SetRequest request = ExampleProto.SetRequest.newBuilder()
                    .setKey(key)
                    .setValue("")
                    .build();

            ExampleProto.SetResponse response = exampleService.set(request);
            
            if (response.getSuccess()) {
                // 删除内存元数据缓存
                metadataCache.remove(key);
                
                // 删除Redis缓存
                cacheService.delete(buildCacheKey(key));
                
                log.info("成功删除键值对: key={}", key);
                return true;
            } else {
                log.warn("删除键值对失败: key={}", key);
                return false;
            }
        } catch (Exception e) {
            log.error("删除键值对异常: key={}", key, e);
            return false;
        } finally {
            monitoringService.recordStorageDuration(sample, "delete");
        }
    }

    @Override
    public boolean exists(String key) {
        StorageResponse response = get(key);
        return response.getExists();
    }

    @Override
    public List<String> keys(String prefix, int limit, int offset) {
        try {
            // 从元数据缓存中获取键列表
            List<String> allKeys = metadataCache.keySet().stream()
                    .filter(key -> {
                        StorageMetadata metadata = metadataCache.get(key);
                        // 过滤过期的键
                        return metadata.getExpireTime() == null || 
                               metadata.getExpireTime() > System.currentTimeMillis();
                    })
                    .collect(Collectors.toList());

            // 按前缀过滤
            if (StringUtils.hasText(prefix)) {
                allKeys = allKeys.stream()
                        .filter(key -> key.startsWith(prefix))
                        .collect(Collectors.toList());
            }

            // 排序
            Collections.sort(allKeys);

            // 分页
            int start = Math.min(offset, allKeys.size());
            int end = Math.min(offset + limit, allKeys.size());
            
            List<String> result = allKeys.subList(start, end);
            log.info("获取键列表: prefix={}, limit={}, offset={}, total={}, returned={}", 
                    prefix, limit, offset, allKeys.size(), result.size());
            return result;
        } catch (Exception e) {
            log.error("获取键列表异常: prefix={}", prefix, e);
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Boolean> batchSet(List<StorageRequest> requests) {
        Map<String, Boolean> results = new HashMap<>();
        
        for (StorageRequest request : requests) {
            boolean success = set(request.getKey(), request.getValue(), request.getTtl());
            results.put(request.getKey(), success);
        }
        
        log.info("批量存储完成: total={}, success={}", 
                requests.size(), results.values().stream().mapToInt(b -> b ? 1 : 0).sum());
        return results;
    }

    @Override
    public Map<String, StorageResponse> batchGet(List<String> keys) {
        Map<String, StorageResponse> results = new HashMap<>();
        
        for (String key : keys) {
            StorageResponse response = get(key);
            results.put(key, response);
        }
        
        log.info("批量获取完成: total={}, exists={}", 
                keys.size(), results.values().stream().mapToInt(r -> r.getExists() ? 1 : 0).sum());
        return results;
    }

    @Override
    public StorageResponse uploadFile(MultipartFile file, String key) {
        try {
            // 生成文件键名
            String fileKey = StringUtils.hasText(key) ? key : generateFileKey(file.getOriginalFilename());
            
            // 将文件内容转换为Base64存储
            byte[] fileBytes = file.getBytes();
            String base64Content = Base64.getEncoder().encodeToString(fileBytes);
            
            // 构造文件元数据
            Map<String, Object> fileMetadata = new HashMap<>();
            fileMetadata.put("originalName", file.getOriginalFilename());
            fileMetadata.put("contentType", file.getContentType());
            fileMetadata.put("size", file.getSize());
            fileMetadata.put("content", base64Content);
            
            // 存储文件
            String metadataJson = new com.google.gson.Gson().toJson(fileMetadata);
            boolean success = set(fileKey, metadataJson, null);
            
            if (success) {
                log.info("文件上传成功: key={}, filename={}, size={}", 
                        fileKey, file.getOriginalFilename(), file.getSize());
                return StorageResponse.builder()
                        .key(fileKey)
                        .value("文件上传成功")
                        .exists(true)
                        .createTime(System.currentTimeMillis())
                        .updateTime(System.currentTimeMillis())
                        .build();
            } else {
                throw new RuntimeException("文件存储失败");
            }
        } catch (Exception e) {
            log.error("文件上传异常: filename={}", file.getOriginalFilename(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadFile(String key, HttpServletResponse response) {
        try {
            StorageResponse storageResponse = get(key);
            
            if (!storageResponse.getExists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件不存在");
                return;
            }
            
            // 解析文件元数据
            String metadataJson = storageResponse.getValue();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            @SuppressWarnings("unchecked")
            Map<String, Object> fileMetadata = gson.fromJson(metadataJson, Map.class);
            
            String originalName = (String) fileMetadata.get("originalName");
            String contentType = (String) fileMetadata.get("contentType");
            String base64Content = (String) fileMetadata.get("content");
            
            // 解码文件内容
            byte[] fileBytes = Base64.getDecoder().decode(base64Content);
            
            // 设置响应头
            response.setContentType(contentType != null ? contentType : "application/octet-stream");
            response.setHeader("Content-Disposition", 
                    "attachment; filename=\"" + (originalName != null ? originalName : key) + "\"");
            response.setContentLength(fileBytes.length);
            
            // 写入文件内容
            response.getOutputStream().write(fileBytes);
            response.getOutputStream().flush();
            
            log.info("文件下载成功: key={}, filename={}, size={}", 
                    key, originalName, fileBytes.length);
        } catch (Exception e) {
            log.error("文件下载异常: key={}", key, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (IOException ioException) {
                log.error("响应写入异常", ioException);
            }
        }
    }

    @Override
    public Map<String, Object> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 统计键数量
            long totalKeys = metadataCache.size();
            long validKeys = metadataCache.values().stream()
                    .mapToLong(metadata -> {
                        return metadata.getExpireTime() == null || 
                               metadata.getExpireTime() > System.currentTimeMillis() ? 1 : 0;
                    })
                    .sum();
            
            // 统计存储大小
            long totalSize = metadataCache.values().stream()
                    .mapToLong(metadata -> metadata.getValue() != null ? 
                            metadata.getValue().getBytes(StandardCharsets.UTF_8).length : 0)
                    .sum();
            
            stats.put("totalKeys", totalKeys);
            stats.put("validKeys", validKeys);
            stats.put("expiredKeys", totalKeys - validKeys);
            stats.put("totalSizeBytes", totalSize);
            stats.put("totalSizeMB", totalSize / 1024.0 / 1024.0);
            stats.put("lastUpdateTime", System.currentTimeMillis());
            
            log.info("获取存储统计信息: totalKeys={}, validKeys={}, totalSizeMB={}", 
                    totalKeys, validKeys, stats.get("totalSizeMB"));
            return stats;
        } catch (Exception e) {
            log.error("获取存储统计信息异常", e);
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", "获取统计信息失败: " + e.getMessage());
            return errorStats;
        }
    }

    /**
     * 生成文件key
     */
    private String generateFileKey(String originalFilename) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = "";
        
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        return "file_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
    }
    
    /**
     * 判断是否为热点数据
     * @param key 数据键
     * @param value 数据值
     * @return 是否为热点数据
     */
    private boolean isHotData(String key, String value) {
        // 热点数据判断策略：
        // 1. 小于1KB的数据（快速访问）
        // 2. 特定前缀的数据（如配置、用户信息等）
        // 3. 最近访问频繁的数据（可以通过访问计数实现）
        
        if (value == null) {
            return false;
        }
        
        // 小数据优先缓存
        if (value.length() <= 1024) {
            return true;
        }
        
        // 配置类数据
        if (key.startsWith("config:") || key.startsWith("setting:")) {
            return true;
        }
        
        // 用户相关数据
        if (key.startsWith("user:") || key.startsWith("session:")) {
            return true;
        }
        
        // 元数据
        if (key.startsWith("meta:") || key.endsWith(":metadata")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 缓存热点数据到Redis
     * @param key 数据键
     * @param value 数据值
     * @param ttl 过期时间（秒）
     */
    private void cacheHotData(String key, String value, Long ttl) {
        try {
            String cacheKey = buildCacheKey(key);
            StorageResponse cacheValue = new StorageResponse();
            cacheValue.setKey(key);
            cacheValue.setValue(value);
            cacheValue.setExists(true);
            cacheValue.setCreateTime(System.currentTimeMillis());
            cacheValue.setUpdateTime(System.currentTimeMillis());
            
            if (ttl != null && ttl > 0) {
                cacheValue.setExpireTime(System.currentTimeMillis() + ttl * 1000);
                cacheService.set(cacheKey, cacheValue, Duration.ofSeconds(ttl));
            } else {
                // 默认缓存5分钟
                cacheService.set(cacheKey, cacheValue, Duration.ofMinutes(5));
            }
            
            log.debug("缓存热点数据: key={}, size={}", cacheKey, value.length());
        } catch (Exception e) {
            log.error("缓存热点数据失败: key={}", key, e);
        }
    }
    
    /**
     * 从Redis缓存获取数据
     * @param key 数据键
     * @return 缓存的响应对象，如果不存在返回null
     */
    private StorageResponse getFromCache(String key) {
        try {
            String cacheKey = buildCacheKey(key);
            StorageResponse cached = cacheService.get(cacheKey, StorageResponse.class);
            
            if (cached != null) {
                // 检查缓存数据是否过期
                if (cached.getExpireTime() != null && cached.getExpireTime() < System.currentTimeMillis()) {
                    // 缓存过期，删除并返回null
                    cacheService.delete(cacheKey);
                    log.debug("缓存数据已过期: key={}", cacheKey);
                    return null;
                }
                
                log.debug("缓存命中: key={}", cacheKey);
                return cached;
            }
            
            return null;
        } catch (Exception e) {
            log.error("从缓存获取数据失败: key={}", key, e);
            return null;
        }
    }
    
    /**
     * 构建缓存键
     * @param key 原始键
     * @return 缓存键
     */
    private String buildCacheKey(String key) {
        return "storage:" + key;
    }
    
    /**
     * 预热常用数据到缓存
     * @param keys 需要预热的键列表
     */
    public void warmUpCache(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        
        log.info("开始预热存储缓存: keys={}", keys.size());
        
        for (String key : keys) {
            try {
                // 从存储获取数据并缓存
                StorageResponse response = get(key);
                if (response != null && response.getExists()) {
                    log.debug("预热缓存成功: key={}", key);
                }
            } catch (Exception e) {
                log.error("预热缓存失败: key={}", key, e);
            }
        }
        
        log.info("存储缓存预热完成");
    }

    /**
     * 存储元数据内部类
     */
    private static class StorageMetadata {
        private String key;
        private String value;
        private Long createTime;
        private Long updateTime;
        private Long expireTime;

        // Getters and Setters
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
        
        public Long getUpdateTime() { return updateTime; }
        public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
        
        public Long getExpireTime() { return expireTime; }
        public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }
    }
} 