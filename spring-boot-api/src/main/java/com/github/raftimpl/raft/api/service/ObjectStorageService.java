package com.github.raftimpl.raft.api.service;

import com.github.raftimpl.raft.api.dto.ObjectMetadata;
import com.github.raftimpl.raft.api.dto.ObjectStorageRequest;
import com.github.raftimpl.raft.api.dto.ObjectStorageResponse;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 对象存储服务接口
 * 提供企业级文件对象存储功能，支持大文件、分片上传、元数据管理等
 * 
 */
public interface ObjectStorageService {

    /**
     * 上传文件对象
     * 
     * @param file 文件
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param metadata 自定义元数据
     * @return 上传结果
     */
    ObjectStorageResponse putObject(MultipartFile file, String bucket, String objectKey, Map<String, String> metadata);

    /**
     * 上传文件对象（流式）
     * 
     * @param inputStream 输入流
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param contentLength 内容长度
     * @param contentType 内容类型
     * @param metadata 自定义元数据
     * @return 上传结果
     */
    ObjectStorageResponse putObject(InputStream inputStream, String bucket, String objectKey, 
                                   long contentLength, String contentType, Map<String, String> metadata);

    /**
     * 获取文件对象
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @return 对象响应
     */
    ObjectStorageResponse getObject(String bucket, String objectKey);

    /**
     * 下载文件对象到HTTP响应
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param response HTTP响应
     */
    void downloadObject(String bucket, String objectKey, HttpServletResponse response);

    /**
     * 删除文件对象
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @return 是否成功
     */
    boolean deleteObject(String bucket, String objectKey);

    /**
     * 批量删除文件对象
     * 
     * @param bucket 存储桶名称
     * @param objectKeys 对象键名列表
     * @return 删除结果映射
     */
    Map<String, Boolean> deleteObjects(String bucket, List<String> objectKeys);

    /**
     * 检查对象是否存在
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @return 是否存在
     */
    boolean objectExists(String bucket, String objectKey);

    /**
     * 获取对象元数据
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @return 对象元数据
     */
    ObjectMetadata getObjectMetadata(String bucket, String objectKey);

    /**
     * 列出存储桶中的对象
     * 
     * @param bucket 存储桶名称
     * @param prefix 对象键前缀过滤
     * @param maxKeys 最大返回数量
     * @param marker 分页标记
     * @return 对象列表
     */
    List<ObjectMetadata> listObjects(String bucket, String prefix, int maxKeys, String marker);

    /**
     * 创建存储桶
     * 
     * @param bucket 存储桶名称
     * @return 是否成功
     */
    boolean createBucket(String bucket);

    /**
     * 删除存储桶
     * 
     * @param bucket 存储桶名称
     * @return 是否成功
     */
    boolean deleteBucket(String bucket);

    /**
     * 列出所有存储桶
     * 
     * @return 存储桶列表
     */
    List<String> listBuckets();

    /**
     * 检查存储桶是否存在
     * 
     * @param bucket 存储桶名称
     * @return 是否存在
     */
    boolean bucketExists(String bucket);

    /**
     * 分片上传 - 初始化
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param contentType 内容类型
     * @param metadata 自定义元数据
     * @return 上传ID
     */
    String initiateMultipartUpload(String bucket, String objectKey, String contentType, Map<String, String> metadata);

    /**
     * 分片上传 - 上传分片
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param uploadId 上传ID
     * @param partNumber 分片号
     * @param inputStream 分片数据流
     * @param partSize 分片大小
     * @return 分片ETag
     */
    String uploadPart(String bucket, String objectKey, String uploadId, int partNumber, 
                     InputStream inputStream, long partSize);

    /**
     * 分片上传 - 完成上传
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param uploadId 上传ID
     * @param partETags 分片ETag列表
     * @return 上传结果
     */
    ObjectStorageResponse completeMultipartUpload(String bucket, String objectKey, String uploadId, 
                                                 List<String> partETags);

    /**
     * 分片上传 - 取消上传
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param uploadId 上传ID
     * @return 是否成功
     */
    boolean abortMultipartUpload(String bucket, String objectKey, String uploadId);

    /**
     * 复制对象
     * 
     * @param sourceBucket 源存储桶
     * @param sourceKey 源对象键
     * @param destBucket 目标存储桶
     * @param destKey 目标对象键
     * @param metadata 新的元数据（可选）
     * @return 复制结果
     */
    ObjectStorageResponse copyObject(String sourceBucket, String sourceKey, String destBucket, 
                                   String destKey, Map<String, String> metadata);

    /**
     * 生成预签名URL
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param expiration 过期时间（秒）
     * @param method HTTP方法（GET/PUT/DELETE）
     * @return 预签名URL
     */
    String generatePresignedUrl(String bucket, String objectKey, int expiration, String method);

    /**
     * 获取存储统计信息
     * 
     * @return 统计信息
     */
    Map<String, Object> getStorageStats();

    /**
     * 数据生命周期管理 - 设置对象存储类型
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param storageClass 存储类型（STANDARD/IA/ARCHIVE/DEEP_ARCHIVE）
     * @return 是否成功
     */
    boolean setObjectStorageClass(String bucket, String objectKey, String storageClass);

    /**
     * 数据生命周期管理 - 恢复归档对象
     * 
     * @param bucket 存储桶名称
     * @param objectKey 对象键名
     * @param days 恢复天数
     * @return 是否成功
     */
    boolean restoreObject(String bucket, String objectKey, int days);
} 