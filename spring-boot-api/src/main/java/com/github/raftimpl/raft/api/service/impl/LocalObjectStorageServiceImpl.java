package com.github.raftimpl.raft.api.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.raftimpl.raft.api.dto.ObjectMetadata;
import com.github.raftimpl.raft.api.dto.ObjectStorageResponse;
import com.github.raftimpl.raft.api.dto.StorageResponse;
import com.github.raftimpl.raft.api.service.ObjectStorageService;
import com.github.raftimpl.raft.api.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 本地文件系统对象存储服务实现
 * 基于本地文件系统提供S3兼容的对象存储功能
 * 
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalObjectStorageServiceImpl implements ObjectStorageService {

    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    @Value("${object-storage.local.base-path:/tmp/raft-object-storage}")
    private String basePath;

    @Value("${object-storage.local.max-file-size:104857600}") // 100MB
    private long maxFileSize;

    @Value("${object-storage.local.multipart-threshold:5242880}") // 5MB
    private long multipartThreshold;

    @Value("${object-storage.local.part-size:5242880}") // 5MB
    private long defaultPartSize;

    // 分片上传会话缓存
    private final Map<String, MultipartUploadSession> multipartSessions = new ConcurrentHashMap<>();

    @Override
    public ObjectStorageResponse putObject(MultipartFile file, String bucket, String objectKey, Map<String, String> metadata) {
        try {
            // 验证参数
            if (file == null || file.isEmpty()) {
                return ObjectStorageResponse.failure("文件不能为空");
            }
            if (file.getSize() > maxFileSize) {
                return ObjectStorageResponse.failure("文件大小超过限制: " + maxFileSize + " 字节");
            }

            // 创建存储桶目录
            if (!createBucketIfNotExists(bucket)) {
                return ObjectStorageResponse.failure("创建存储桶失败: " + bucket);
            }

            // 构建文件路径
            Path objectPath = buildObjectPath(bucket, objectKey);
            Files.createDirectories(objectPath.getParent());

            // 保存文件
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, objectPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 计算ETag（MD5）
            String etag = calculateFileETag(objectPath);

            // 构建对象元数据
            ObjectMetadata objectMetadata = buildObjectMetadata(bucket, objectKey, file, etag, metadata);

            // 保存元数据到Raft存储
            saveObjectMetadata(objectMetadata);

            log.info("对象上传成功: bucket={}, key={}, size={}, etag={}", 
                    bucket, objectKey, file.getSize(), etag);

            ObjectStorageResponse response = ObjectStorageResponse.success("文件上传成功", bucket, objectKey, etag);
            response.setSize(file.getSize());
            response.setContentType(file.getContentType());
            response.setMetadata(metadata);
            return response;

        } catch (Exception e) {
            log.error("对象上传失败: bucket={}, key={}", bucket, objectKey, e);
            return ObjectStorageResponse.failure("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public ObjectStorageResponse putObject(InputStream inputStream, String bucket, String objectKey, 
                                         long contentLength, String contentType, Map<String, String> metadata) {
        try {
            // 验证参数
            if (inputStream == null) {
                return ObjectStorageResponse.failure("输入流不能为空");
            }
            if (contentLength > maxFileSize) {
                return ObjectStorageResponse.failure("文件大小超过限制: " + maxFileSize + " 字节");
            }

            // 创建存储桶目录
            if (!createBucketIfNotExists(bucket)) {
                return ObjectStorageResponse.failure("创建存储桶失败: " + bucket);
            }

            // 构建文件路径
            Path objectPath = buildObjectPath(bucket, objectKey);
            Files.createDirectories(objectPath.getParent());

            // 保存文件
            Files.copy(inputStream, objectPath, StandardCopyOption.REPLACE_EXISTING);

            // 计算ETag（MD5）
            String etag = calculateFileETag(objectPath);

            // 构建对象元数据
            ObjectMetadata objectMetadata = ObjectMetadata.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .size(contentLength)
                    .contentType(contentType)
                    .etag(etag)
                    .createdTime(System.currentTimeMillis())
                    .lastModified(System.currentTimeMillis())
                    .storageClass("STANDARD")
                    .userMetadata(metadata != null ? metadata : new HashMap<>())
                    .filePath(objectPath.toString())
                    .build();

            // 保存元数据到Raft存储
            saveObjectMetadata(objectMetadata);

            log.info("对象上传成功（流式）: bucket={}, key={}, size={}, etag={}", 
                    bucket, objectKey, contentLength, etag);

            ObjectStorageResponse response = ObjectStorageResponse.success("文件上传成功", bucket, objectKey, etag);
            response.setSize(contentLength);
            response.setContentType(contentType);
            response.setMetadata(metadata);
            return response;

        } catch (Exception e) {
            log.error("对象上传失败（流式）: bucket={}, key={}", bucket, objectKey, e);
            return ObjectStorageResponse.failure("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public ObjectStorageResponse getObject(String bucket, String objectKey) {
        try {
            // 获取对象元数据
            ObjectMetadata metadata = getObjectMetadata(bucket, objectKey);
            if (metadata == null) {
                return ObjectStorageResponse.failure("对象不存在: " + bucket + "/" + objectKey);
            }

            // 构建文件路径
            Path objectPath = buildObjectPath(bucket, objectKey);
            if (!Files.exists(objectPath)) {
                return ObjectStorageResponse.failure("文件不存在: " + objectPath);
            }

            // 创建文件输入流
            InputStream dataStream = Files.newInputStream(objectPath);

            // 更新访问时间
            metadata.setLastAccessTime(System.currentTimeMillis());
            metadata.setAccessCount(metadata.getAccessCount() != null ? metadata.getAccessCount() + 1 : 1);
            saveObjectMetadata(metadata);

            log.info("对象获取成功: bucket={}, key={}, size={}", bucket, objectKey, metadata.getSize());

            return ObjectStorageResponse.builder()
                    .success(true)
                    .message("文件获取成功")
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .etag(metadata.getEtag())
                    .size(metadata.getSize())
                    .contentType(metadata.getContentType())
                    .lastModified(metadata.getLastModified())
                    .createdTime(metadata.getCreatedTime())
                    .storageClass(metadata.getStorageClass())
                    .metadata(metadata.getUserMetadata())
                    .dataStream(dataStream)
                    .build();

        } catch (Exception e) {
            log.error("对象获取失败: bucket={}, key={}", bucket, objectKey, e);
            return ObjectStorageResponse.failure("文件获取失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadObject(String bucket, String objectKey, HttpServletResponse response) {
        try {
            ObjectStorageResponse objectResponse = getObject(bucket, objectKey);
            if (!objectResponse.getSuccess()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write(objectResponse.getMessage());
                return;
            }

            // 设置响应头
            response.setContentType(objectResponse.getContentType() != null ? 
                    objectResponse.getContentType() : "application/octet-stream");
            response.setHeader("Content-Disposition", 
                    "attachment; filename=\"" + getFileName(objectKey) + "\"");
            response.setHeader("ETag", objectResponse.getEtag());
            response.setHeader("Last-Modified", String.valueOf(objectResponse.getLastModified()));
            
            if (objectResponse.getSize() != null) {
                response.setContentLengthLong(objectResponse.getSize());
            }

            // 写入文件内容
            try (InputStream inputStream = objectResponse.getDataStream();
                 OutputStream outputStream = response.getOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            log.info("对象下载成功: bucket={}, key={}", bucket, objectKey);

        } catch (Exception e) {
            log.error("对象下载失败: bucket={}, key={}", bucket, objectKey, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (IOException ioException) {
                log.error("响应写入异常", ioException);
            }
        }
    }

    @Override
    public boolean deleteObject(String bucket, String objectKey) {
        try {
            // 删除文件
            Path objectPath = buildObjectPath(bucket, objectKey);
            if (Files.exists(objectPath)) {
                Files.delete(objectPath);
            }

            // 删除元数据
            String metadataKey = buildMetadataKey(bucket, objectKey);
            storageService.delete(metadataKey);

            log.info("对象删除成功: bucket={}, key={}", bucket, objectKey);
            return true;

        } catch (Exception e) {
            log.error("对象删除失败: bucket={}, key={}", bucket, objectKey, e);
            return false;
        }
    }

    @Override
    public Map<String, Boolean> deleteObjects(String bucket, List<String> objectKeys) {
        Map<String, Boolean> results = new HashMap<>();
        
        for (String objectKey : objectKeys) {
            boolean success = deleteObject(bucket, objectKey);
            results.put(objectKey, success);
        }
        
        log.info("批量删除对象完成: bucket={}, total={}, success={}", 
                bucket, objectKeys.size(), results.values().stream().mapToInt(b -> b ? 1 : 0).sum());
        return results;
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        try {
            Path objectPath = buildObjectPath(bucket, objectKey);
            return Files.exists(objectPath) && getObjectMetadata(bucket, objectKey) != null;
        } catch (Exception e) {
            log.error("检查对象是否存在失败: bucket={}, key={}", bucket, objectKey, e);
            return false;
        }
    }

    @Override
    public ObjectMetadata getObjectMetadata(String bucket, String objectKey) {
        try {
            String metadataKey = buildMetadataKey(bucket, objectKey);
            StorageResponse storageResponse = storageService.get(metadataKey);
            
            if (!storageResponse.getExists()) {
                return null;
            }

            return objectMapper.readValue(storageResponse.getValue(), ObjectMetadata.class);

        } catch (Exception e) {
            log.error("获取对象元数据失败: bucket={}, key={}", bucket, objectKey, e);
            return null;
        }
    }

    @Override
    public List<ObjectMetadata> listObjects(String bucket, String prefix, int maxKeys, String marker) {
        try {
            List<ObjectMetadata> objects = new ArrayList<>();
            
            // 从Raft存储中查询对象元数据
            String searchPrefix = "object_metadata:" + bucket + ":";
            if (StringUtils.hasText(prefix)) {
                searchPrefix += prefix;
            }
            
            List<String> keys = storageService.keys(searchPrefix, maxKeys * 2, 0);
            
            for (String key : keys) {
                try {
                    StorageResponse storageResponse = storageService.get(key);
                    if (storageResponse.getExists()) {
                        ObjectMetadata metadata = objectMapper.readValue(storageResponse.getValue(), ObjectMetadata.class);
                        
                        // 应用前缀过滤
                        if (!StringUtils.hasText(prefix) || metadata.getObjectKey().startsWith(prefix)) {
                            // 应用marker过滤
                            if (!StringUtils.hasText(marker) || metadata.getObjectKey().compareTo(marker) > 0) {
                                objects.add(metadata);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析对象元数据失败: key={}", key, e);
                }
                
                if (objects.size() >= maxKeys) {
                    break;
                }
            }
            
            // 按对象键排序
            objects.sort(Comparator.comparing(ObjectMetadata::getObjectKey));
            
            log.info("列出对象成功: bucket={}, prefix={}, count={}", bucket, prefix, objects.size());
            return objects;

        } catch (Exception e) {
            log.error("列出对象失败: bucket={}, prefix={}", bucket, prefix, e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean createBucket(String bucket) {
        try {
            Path bucketPath = Paths.get(basePath, bucket);
            Files.createDirectories(bucketPath);
            
            // 保存存储桶元数据
            String bucketKey = "bucket_metadata:" + bucket;
            Map<String, Object> bucketMetadata = new HashMap<>();
            bucketMetadata.put("name", bucket);
            bucketMetadata.put("createdTime", System.currentTimeMillis());
            bucketMetadata.put("region", "local");
            
            storageService.set(bucketKey, objectMapper.writeValueAsString(bucketMetadata), null);
            
            log.info("存储桶创建成功: bucket={}", bucket);
            return true;

        } catch (Exception e) {
            log.error("存储桶创建失败: bucket={}", bucket, e);
            return false;
        }
    }

    @Override
    public boolean deleteBucket(String bucket) {
        try {
            // 检查存储桶是否为空
            List<ObjectMetadata> objects = listObjects(bucket, null, 1, null);
            if (!objects.isEmpty()) {
                log.warn("存储桶不为空，无法删除: bucket={}", bucket);
                return false;
            }

            // 删除存储桶目录
            Path bucketPath = Paths.get(basePath, bucket);
            if (Files.exists(bucketPath)) {
                Files.delete(bucketPath);
            }

            // 删除存储桶元数据
            String bucketKey = "bucket_metadata:" + bucket;
            storageService.delete(bucketKey);

            log.info("存储桶删除成功: bucket={}", bucket);
            return true;

        } catch (Exception e) {
            log.error("存储桶删除失败: bucket={}", bucket, e);
            return false;
        }
    }

    @Override
    public List<String> listBuckets() {
        try {
            List<String> buckets = new ArrayList<>();
            List<String> keys = storageService.keys("bucket_metadata:", 1000, 0);
            
            for (String key : keys) {
                String bucketName = key.substring("bucket_metadata:".length());
                buckets.add(bucketName);
            }
            
            buckets.sort(String::compareTo);
            log.info("列出存储桶成功: count={}", buckets.size());
            return buckets;

        } catch (Exception e) {
            log.error("列出存储桶失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean bucketExists(String bucket) {
        try {
            String bucketKey = "bucket_metadata:" + bucket;
            return storageService.exists(bucketKey);
        } catch (Exception e) {
            log.error("检查存储桶是否存在失败: bucket={}", bucket, e);
            return false;
        }
    }

    // 分片上传相关方法（简化实现）
    @Override
    public String initiateMultipartUpload(String bucket, String objectKey, String contentType, Map<String, String> metadata) {
        try {
            String uploadId = UUID.randomUUID().toString();
            MultipartUploadSession session = new MultipartUploadSession();
            session.setBucket(bucket);
            session.setObjectKey(objectKey);
            session.setContentType(contentType);
            session.setMetadata(metadata);
            session.setUploadId(uploadId);
            session.setCreatedTime(System.currentTimeMillis());
            session.setParts(new ArrayList<>());
            
            multipartSessions.put(uploadId, session);
            
            log.info("分片上传初始化成功: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId);
            return uploadId;

        } catch (Exception e) {
            log.error("分片上传初始化失败: bucket={}, key={}", bucket, objectKey, e);
            return null;
        }
    }

    @Override
    public String uploadPart(String bucket, String objectKey, String uploadId, int partNumber, 
                           InputStream inputStream, long partSize) {
        try {
            MultipartUploadSession session = multipartSessions.get(uploadId);
            if (session == null) {
                throw new IllegalArgumentException("无效的上传ID: " + uploadId);
            }

            // 保存分片文件
            Path partPath = Paths.get(basePath, bucket, ".multipart", uploadId, "part_" + partNumber);
            Files.createDirectories(partPath.getParent());
            Files.copy(inputStream, partPath, StandardCopyOption.REPLACE_EXISTING);

            // 计算分片ETag
            String partETag = calculateFileETag(partPath);

            // 记录分片信息
            PartInfo partInfo = new PartInfo();
            partInfo.setPartNumber(partNumber);
            partInfo.setEtag(partETag);
            partInfo.setSize(partSize);
            partInfo.setFilePath(partPath.toString());
            
            session.getParts().add(partInfo);
            
            log.info("分片上传成功: bucket={}, key={}, uploadId={}, partNumber={}, etag={}", 
                    bucket, objectKey, uploadId, partNumber, partETag);
            return partETag;

        } catch (Exception e) {
            log.error("分片上传失败: bucket={}, key={}, uploadId={}, partNumber={}", 
                    bucket, objectKey, uploadId, partNumber, e);
            return null;
        }
    }

    @Override
    public ObjectStorageResponse completeMultipartUpload(String bucket, String objectKey, String uploadId, 
                                                       List<String> partETags) {
        try {
            MultipartUploadSession session = multipartSessions.get(uploadId);
            if (session == null) {
                return ObjectStorageResponse.failure("无效的上传ID: " + uploadId);
            }

            // 合并分片文件
            Path objectPath = buildObjectPath(bucket, objectKey);
            Files.createDirectories(objectPath.getParent());

            try (OutputStream outputStream = Files.newOutputStream(objectPath)) {
                for (PartInfo part : session.getParts()) {
                    Path partPath = Paths.get(part.getFilePath());
                    if (Files.exists(partPath)) {
                        Files.copy(partPath, outputStream);
                    }
                }
            }

            // 计算最终ETag
            String etag = calculateFileETag(objectPath);
            long totalSize = Files.size(objectPath);

            // 构建对象元数据
            ObjectMetadata objectMetadata = ObjectMetadata.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .size(totalSize)
                    .contentType(session.getContentType())
                    .etag(etag)
                    .createdTime(System.currentTimeMillis())
                    .lastModified(System.currentTimeMillis())
                    .storageClass("STANDARD")
                    .userMetadata(session.getMetadata() != null ? session.getMetadata() : new HashMap<>())
                    .filePath(objectPath.toString())
                    .build();

            // 保存元数据
            saveObjectMetadata(objectMetadata);

            // 清理分片文件和会话
            cleanupMultipartUpload(uploadId);

            log.info("分片上传完成: bucket={}, key={}, uploadId={}, size={}, etag={}", 
                    bucket, objectKey, uploadId, totalSize, etag);

            ObjectStorageResponse response = ObjectStorageResponse.success("分片上传完成", bucket, objectKey, etag);
            response.setSize(totalSize);
            response.setContentType(session.getContentType());
            return response;

        } catch (Exception e) {
            log.error("分片上传完成失败: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId, e);
            return ObjectStorageResponse.failure("分片上传完成失败: " + e.getMessage());
        }
    }

    @Override
    public boolean abortMultipartUpload(String bucket, String objectKey, String uploadId) {
        try {
            cleanupMultipartUpload(uploadId);
            log.info("分片上传取消成功: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId);
            return true;

        } catch (Exception e) {
            log.error("分片上传取消失败: bucket={}, key={}, uploadId={}", bucket, objectKey, uploadId, e);
            return false;
        }
    }

    @Override
    public ObjectStorageResponse copyObject(String sourceBucket, String sourceKey, String destBucket, 
                                          String destKey, Map<String, String> metadata) {
        try {
            // 获取源对象
            ObjectStorageResponse sourceObject = getObject(sourceBucket, sourceKey);
            if (!sourceObject.getSuccess()) {
                return ObjectStorageResponse.failure("源对象不存在: " + sourceBucket + "/" + sourceKey);
            }

            // 复制到目标位置
            ObjectStorageResponse result = putObject(sourceObject.getDataStream(), destBucket, destKey, 
                    sourceObject.getSize(), sourceObject.getContentType(), metadata);

            if (result.getSuccess()) {
                // 设置复制响应信息
                ObjectStorageResponse.CopyResponse copyResponse = ObjectStorageResponse.CopyResponse.builder()
                        .sourceBucket(sourceBucket)
                        .sourceKey(sourceKey)
                        .destBucket(destBucket)
                        .destKey(destKey)
                        .copyTime(System.currentTimeMillis())
                        .build();
                
                result.setCopyResponse(copyResponse);
                log.info("对象复制成功: {}:{} -> {}:{}", sourceBucket, sourceKey, destBucket, destKey);
            }

            return result;

        } catch (Exception e) {
            log.error("对象复制失败: {}:{} -> {}:{}", sourceBucket, sourceKey, destBucket, destKey, e);
            return ObjectStorageResponse.failure("对象复制失败: " + e.getMessage());
        }
    }

    @Override
    public String generatePresignedUrl(String bucket, String objectKey, int expiration, String method) {
        // 简化实现，实际应用中需要实现JWT签名
        try {
            String baseUrl = "http://localhost:8080/api/object-storage";
            String timestamp = String.valueOf(System.currentTimeMillis() + expiration * 1000);
            String signature = DigestUtils.md5DigestAsHex((bucket + objectKey + timestamp + method).getBytes());
            
            return String.format("%s/%s/%s?expires=%s&signature=%s&method=%s", 
                    baseUrl, bucket, objectKey, timestamp, signature, method);

        } catch (Exception e) {
            log.error("生成预签名URL失败: bucket={}, key={}", bucket, objectKey, e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getStorageStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 统计存储桶数量
            List<String> buckets = listBuckets();
            stats.put("bucketCount", buckets.size());
            
            // 统计对象数量和总大小
            long totalObjects = 0;
            long totalSize = 0;
            
            for (String bucket : buckets) {
                List<ObjectMetadata> objects = listObjects(bucket, null, Integer.MAX_VALUE, null);
                totalObjects += objects.size();
                totalSize += objects.stream().mapToLong(obj -> obj.getSize() != null ? obj.getSize() : 0).sum();
            }
            
            stats.put("totalObjects", totalObjects);
            stats.put("totalSizeBytes", totalSize);
            stats.put("totalSizeMB", totalSize / 1024.0 / 1024.0);
            stats.put("totalSizeGB", totalSize / 1024.0 / 1024.0 / 1024.0);
            stats.put("lastUpdateTime", System.currentTimeMillis());
            
            log.info("获取存储统计信息: buckets={}, objects={}, sizeMB={}", 
                    buckets.size(), totalObjects, stats.get("totalSizeMB"));
            return stats;

        } catch (Exception e) {
            log.error("获取存储统计信息失败", e);
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", "获取统计信息失败: " + e.getMessage());
            return errorStats;
        }
    }

    @Override
    public boolean setObjectStorageClass(String bucket, String objectKey, String storageClass) {
        try {
            ObjectMetadata metadata = getObjectMetadata(bucket, objectKey);
            if (metadata == null) {
                return false;
            }

            metadata.setStorageClass(storageClass);
            metadata.setLastModified(System.currentTimeMillis());
            saveObjectMetadata(metadata);

            log.info("设置对象存储类型成功: bucket={}, key={}, storageClass={}", bucket, objectKey, storageClass);
            return true;

        } catch (Exception e) {
            log.error("设置对象存储类型失败: bucket={}, key={}, storageClass={}", bucket, objectKey, storageClass, e);
            return false;
        }
    }

    @Override
    public boolean restoreObject(String bucket, String objectKey, int days) {
        try {
            ObjectMetadata metadata = getObjectMetadata(bucket, objectKey);
            if (metadata == null) {
                return false;
            }

            // 简化实现：设置恢复时间
            long restoreTime = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L;
            if (metadata.getSystemMetadata() == null) {
                metadata.setSystemMetadata(new HashMap<>());
            }
            metadata.getSystemMetadata().put("restoreUntil", String.valueOf(restoreTime));
            metadata.setLastModified(System.currentTimeMillis());
            saveObjectMetadata(metadata);

            log.info("恢复归档对象成功: bucket={}, key={}, days={}", bucket, objectKey, days);
            return true;

        } catch (Exception e) {
            log.error("恢复归档对象失败: bucket={}, key={}, days={}", bucket, objectKey, days, e);
            return false;
        }
    }

    // 辅助方法

    private boolean createBucketIfNotExists(String bucket) {
        if (!bucketExists(bucket)) {
            return createBucket(bucket);
        }
        return true;
    }

    private Path buildObjectPath(String bucket, String objectKey) {
        return Paths.get(basePath, bucket, objectKey);
    }

    private String buildMetadataKey(String bucket, String objectKey) {
        return "object_metadata:" + bucket + ":" + objectKey;
    }

    private String calculateFileETag(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        return bytesToHex(md.digest());
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private ObjectMetadata buildObjectMetadata(String bucket, String objectKey, MultipartFile file, 
                                             String etag, Map<String, String> metadata) {
        return ObjectMetadata.builder()
                .bucket(bucket)
                .objectKey(objectKey)
                .size(file.getSize())
                .contentType(file.getContentType())
                .etag(etag)
                .createdTime(System.currentTimeMillis())
                .lastModified(System.currentTimeMillis())
                .storageClass("STANDARD")
                .userMetadata(metadata != null ? metadata : new HashMap<>())
                .filePath(buildObjectPath(bucket, objectKey).toString())
                .accessCount(0L)
                .build();
    }

    private void saveObjectMetadata(ObjectMetadata metadata) throws JsonProcessingException {
        String metadataKey = buildMetadataKey(metadata.getBucket(), metadata.getObjectKey());
        String metadataJson = objectMapper.writeValueAsString(metadata);
        storageService.set(metadataKey, metadataJson, null);
    }

    private String getFileName(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
    }

    private void cleanupMultipartUpload(String uploadId) {
        try {
            MultipartUploadSession session = multipartSessions.remove(uploadId);
            if (session != null) {
                // 删除分片文件
                Path multipartDir = Paths.get(basePath, session.getBucket(), ".multipart", uploadId);
                if (Files.exists(multipartDir)) {
                    Files.walk(multipartDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    log.warn("删除分片文件失败: {}", path, e);
                                }
                            });
                }
            }
        } catch (Exception e) {
            log.error("清理分片上传失败: uploadId={}", uploadId, e);
        }
    }

    // 内部类

    private static class MultipartUploadSession {
        private String bucket;
        private String objectKey;
        private String contentType;
        private Map<String, String> metadata;
        private String uploadId;
        private long createdTime;
        private List<PartInfo> parts;

        // Getters and Setters
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        
        public String getObjectKey() { return objectKey; }
        public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
        
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
        
        public String getUploadId() { return uploadId; }
        public void setUploadId(String uploadId) { this.uploadId = uploadId; }
        
        public long getCreatedTime() { return createdTime; }
        public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
        
        public List<PartInfo> getParts() { return parts; }
        public void setParts(List<PartInfo> parts) { this.parts = parts; }
    }

    private static class PartInfo {
        private int partNumber;
        private String etag;
        private long size;
        private String filePath;

        // Getters and Setters
        public int getPartNumber() { return partNumber; }
        public void setPartNumber(int partNumber) { this.partNumber = partNumber; }
        
        public String getEtag() { return etag; }
        public void setEtag(String etag) { this.etag = etag; }
        
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
    }
} 