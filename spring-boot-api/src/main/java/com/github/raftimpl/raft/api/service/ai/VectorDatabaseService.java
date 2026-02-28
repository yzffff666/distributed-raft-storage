package com.github.raftimpl.raft.api.service.ai;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import java.util.List;
import java.util.Map;

/**
 * 向量数据库服务接口
 * 负责文档embedding的存储、检索和相似性搜索
 */
public interface VectorDatabaseService {
    
    /**
     * 存储文档向量
     * @param documentId 文档ID
     * @param content 文档内容
     * @param metadata 元数据
     * @return 存储结果
     */
    ApiResponse<String> storeDocumentVector(String documentId, String content, Map<String, Object> metadata);
    
    /**
     * 搜索相似文档
     * @param query 查询文本
     * @param topK 返回前K个最相似的结果
     * @return 相似文档列表
     */
    ApiResponse<List<SimilarDocument>> searchSimilarDocuments(String query, int topK);
    
    /**
     * 删除文档向量
     * @param documentId 文档ID
     * @return 删除结果
     */
    ApiResponse<Boolean> deleteDocumentVector(String documentId);
    
    /**
     * 获取文档向量
     * @param documentId 文档ID
     * @return 文档向量信息
     */
    ApiResponse<DocumentVector> getDocumentVector(String documentId);
    
    /**
     * 批量存储文档向量
     * @param documents 文档列表
     * @return 批量存储结果
     */
    ApiResponse<Integer> batchStoreDocuments(List<DocumentInfo> documents);
    
    /**
     * 获取向量数据库统计信息
     * @return 统计信息
     */
    ApiResponse<VectorDatabaseStats> getStats();
    
    /**
     * 相似文档信息
     */
    class SimilarDocument {
        private String documentId;
        private String content;
        private double similarity;
        private Map<String, Object> metadata;
        
        // 构造函数
        public SimilarDocument(String documentId, String content, double similarity, Map<String, Object> metadata) {
            this.documentId = documentId;
            this.content = content;
            this.similarity = similarity;
            this.metadata = metadata;
        }
        
        // Getters and Setters
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public double getSimilarity() { return similarity; }
        public void setSimilarity(double similarity) { this.similarity = similarity; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 文档向量信息
     */
    class DocumentVector {
        private String documentId;
        private float[] vector;
        private Map<String, Object> metadata;
        
        // 构造函数
        public DocumentVector(String documentId, float[] vector, Map<String, Object> metadata) {
            this.documentId = documentId;
            this.vector = vector;
            this.metadata = metadata;
        }
        
        // Getters and Setters
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        
        public float[] getVector() { return vector; }
        public void setVector(float[] vector) { this.vector = vector; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 文档信息
     */
    class DocumentInfo {
        private String documentId;
        private String content;
        private Map<String, Object> metadata;
        
        // 构造函数
        public DocumentInfo(String documentId, String content, Map<String, Object> metadata) {
            this.documentId = documentId;
            this.content = content;
            this.metadata = metadata;
        }
        
        // Getters and Setters
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 向量数据库统计信息
     */
    class VectorDatabaseStats {
        private long totalDocuments;
        private long totalVectors;
        private String indexType;
        private double avgSimilarity;
        
        // 构造函数
        public VectorDatabaseStats(long totalDocuments, long totalVectors, String indexType, double avgSimilarity) {
            this.totalDocuments = totalDocuments;
            this.totalVectors = totalVectors;
            this.indexType = indexType;
            this.avgSimilarity = avgSimilarity;
        }
        
        // Getters and Setters
        public long getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }
        
        public long getTotalVectors() { return totalVectors; }
        public void setTotalVectors(long totalVectors) { this.totalVectors = totalVectors; }
        
        public String getIndexType() { return indexType; }
        public void setIndexType(String indexType) { this.indexType = indexType; }
        
        public double getAvgSimilarity() { return avgSimilarity; }
        public void setAvgSimilarity(double avgSimilarity) { this.avgSimilarity = avgSimilarity; }
    }
} 