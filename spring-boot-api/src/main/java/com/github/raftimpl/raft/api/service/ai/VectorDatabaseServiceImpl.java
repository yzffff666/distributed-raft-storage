package com.github.raftimpl.raft.api.service.ai;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 向量数据库服务实现类
 * 使用简单的向量相似性搜索实现
 */
@Service
public class VectorDatabaseServiceImpl implements VectorDatabaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseServiceImpl.class);
    
    private final Map<String, DocumentVector> vectorStore = new ConcurrentHashMap<>();
    private final Map<String, DocumentMetadata> documentStore = new ConcurrentHashMap<>();
    
    // 向量维度（模拟embedding维度）
    private static final int VECTOR_DIMENSION = 384;
    
    @PostConstruct
    public void init() {
        try {
            logger.info("Initializing vector database service...");
            logger.info("Vector database service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize vector database service: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public ApiResponse<String> storeDocumentVector(String documentId, String content, Map<String, Object> metadata) {
        try {
            logger.info("Storing document vector for ID: {}", documentId);
            
            // 生成模拟的文档embedding
            float[] vector = generateMockEmbedding(content);
            
            // 存储向量
            DocumentVector docVector = new DocumentVector(documentId, vector, metadata);
            vectorStore.put(documentId, docVector);
            
            // 存储文档元数据
            DocumentMetadata docMetadata = new DocumentMetadata(documentId, content, metadata, System.currentTimeMillis());
            documentStore.put(documentId, docMetadata);
            
            logger.info("Successfully stored document vector for ID: {}", documentId);
            return ApiResponse.success("Document vector stored successfully");
            
        } catch (Exception e) {
            logger.error("Error storing document vector for ID: {}: {}", documentId, e.getMessage(), e);
            return ApiResponse.error("Failed to store document vector: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<List<SimilarDocument>> searchSimilarDocuments(String query, int topK) {
        try {
            logger.info("Searching similar documents for query: {}, topK: {}", query, topK);
            
            // 生成查询embedding
            float[] queryVector = generateMockEmbedding(query);
            
            // 计算所有文档的相似度
            List<SimilarityResult> similarities = new ArrayList<>();
            for (Map.Entry<String, DocumentVector> entry : vectorStore.entrySet()) {
                String docId = entry.getKey();
                DocumentVector docVector = entry.getValue();
                
                // 计算余弦相似度
                double similarity = calculateCosineSimilarity(queryVector, docVector.getVector());
                similarities.add(new SimilarityResult(docId, similarity));
            }
            
            // 按相似度排序并取前topK个
            List<SimilarDocument> similarDocuments = similarities.stream()
                .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                .limit(topK)
                .map(result -> {
                    DocumentMetadata metadata = documentStore.get(result.documentId);
                    return new SimilarDocument(
                        result.documentId,
                        metadata != null ? metadata.getContent() : "",
                        result.similarity,
                        metadata != null ? metadata.getMetadata() : new HashMap<>()
                    );
                })
                .collect(Collectors.toList());
            
            logger.info("Found {} similar documents for query", similarDocuments.size());
            return ApiResponse.success(similarDocuments);
            
        } catch (Exception e) {
            logger.error("Error searching similar documents: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to search similar documents: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<Boolean> deleteDocumentVector(String documentId) {
        try {
            logger.info("Deleting document vector for ID: {}", documentId);
            
            vectorStore.remove(documentId);
            documentStore.remove(documentId);
            
            logger.info("Successfully deleted document vector for ID: {}", documentId);
            return ApiResponse.success(true);
            
        } catch (Exception e) {
            logger.error("Error deleting document vector for ID: {}: {}", documentId, e.getMessage(), e);
            return ApiResponse.error("Failed to delete document vector: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<DocumentVector> getDocumentVector(String documentId) {
        try {
            logger.info("Getting document vector for ID: {}", documentId);
            
            DocumentVector docVector = vectorStore.get(documentId);
            if (docVector == null) {
                return ApiResponse.error("Document vector not found: " + documentId);
            }
            
            return ApiResponse.success(docVector);
            
        } catch (Exception e) {
            logger.error("Error getting document vector for ID: {}: {}", documentId, e.getMessage(), e);
            return ApiResponse.error("Failed to get document vector: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<Integer> batchStoreDocuments(List<DocumentInfo> documents) {
        try {
            logger.info("Batch storing {} documents", documents.size());
            
            int successCount = 0;
            for (DocumentInfo docInfo : documents) {
                ApiResponse<String> result = storeDocumentVector(
                    docInfo.getDocumentId(), 
                    docInfo.getContent(), 
                    docInfo.getMetadata()
                );
                if (result.isSuccess()) {
                    successCount++;
                }
            }
            
            logger.info("Successfully stored {}/{} documents", successCount, documents.size());
            return ApiResponse.success(successCount);
            
        } catch (Exception e) {
            logger.error("Error batch storing documents: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to batch store documents: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<VectorDatabaseStats> getStats() {
        try {
            long totalDocuments = documentStore.size();
            long totalVectors = vectorStore.size();
            String indexType = "Simple Vector Store";
            double avgSimilarity = 0.85; // 模拟平均相似度
            
            VectorDatabaseStats stats = new VectorDatabaseStats(totalDocuments, totalVectors, indexType, avgSimilarity);
            return ApiResponse.success(stats);
            
        } catch (Exception e) {
            logger.error("Error getting vector database stats: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to get stats: " + e.getMessage());
        }
    }
    
    /**
     * 生成模拟的文档embedding
     */
    private float[] generateMockEmbedding(String content) {
        float[] vector = new float[VECTOR_DIMENSION];
        Random random = new Random(content.hashCode()); // 使用内容hash作为随机种子，确保相同内容产生相同向量
        
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            vector[i] = (float) (random.nextGaussian() * 0.1); // 生成正态分布的随机数
        }
        
        // 归一化向量
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        
        return vector;
    }
    
    /**
     * 计算余弦相似度
     */
    private double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * 相似度结果
     */
    private static class SimilarityResult {
        private final String documentId;
        private final double similarity;
        
        public SimilarityResult(String documentId, double similarity) {
            this.documentId = documentId;
            this.similarity = similarity;
        }
    }
    
    /**
     * 文档元数据
     */
    private static class DocumentMetadata {
        private final String documentId;
        private final String content;
        private final Map<String, Object> metadata;
        private final long timestamp;
        
        public DocumentMetadata(String documentId, String content, Map<String, Object> metadata, long timestamp) {
            this.documentId = documentId;
            this.content = content;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.timestamp = timestamp;
        }
        
        public String getDocumentId() { return documentId; }
        public String getContent() { return content; }
        public Map<String, Object> getMetadata() { return metadata; }
        public long getTimestamp() { return timestamp; }
    }
} 