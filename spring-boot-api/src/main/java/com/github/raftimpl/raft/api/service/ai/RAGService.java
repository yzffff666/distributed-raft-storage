package com.github.raftimpl.raft.api.service.ai;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import java.util.List;
import java.util.Map;

/**
 * RAG（检索增强生成）服务接口
 * 结合向量搜索和生成式AI，提供智能问答功能
 */
public interface RAGService {
    
    /**
     * 智能问答
     * @param question 用户问题
     * @param context 上下文信息
     * @return 答案和相关文档
     */
    ApiResponse<RAGResponse> intelligentQA(String question, Map<String, Object> context);
    
    /**
     * 基于文档的问答
     * @param question 问题
     * @param documentIds 相关文档ID列表
     * @return 基于文档的答案
     */
    ApiResponse<String> documentBasedQA(String question, List<String> documentIds);
    
    /**
     * 语义搜索
     * @param query 搜索查询
     * @param filters 过滤条件
     * @return 搜索结果
     */
    ApiResponse<List<SemanticSearchResult>> semanticSearch(String query, Map<String, Object> filters);
    
    /**
     * 生成智能摘要
     * @param content 内容
     * @param summaryType 摘要类型
     * @return 智能摘要
     */
    ApiResponse<String> generateIntelligentSummary(String content, String summaryType);
    
    /**
     * 自动分类和标签生成
     * @param content 内容
     * @return 分类和标签
     */
    ApiResponse<ClassificationResult> autoClassifyAndTag(String content);
    
    /**
     * 相关性分析
     * @param content1 内容1
     * @param content2 内容2
     * @return 相关性分数和分析
     */
    ApiResponse<RelevanceAnalysis> analyzeRelevance(String content1, String content2);
    
    /**
     * RAG响应
     */
    class RAGResponse {
        private String answer;
        private List<String> sources;
        private double confidence;
        private List<VectorDatabaseService.SimilarDocument> relatedDocuments;
        
        public RAGResponse(String answer, List<String> sources, double confidence, 
                          List<VectorDatabaseService.SimilarDocument> relatedDocuments) {
            this.answer = answer;
            this.sources = sources;
            this.confidence = confidence;
            this.relatedDocuments = relatedDocuments;
        }
        
        // Getters and Setters
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        
        public List<String> getSources() { return sources; }
        public void setSources(List<String> sources) { this.sources = sources; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public List<VectorDatabaseService.SimilarDocument> getRelatedDocuments() { return relatedDocuments; }
        public void setRelatedDocuments(List<VectorDatabaseService.SimilarDocument> relatedDocuments) { 
            this.relatedDocuments = relatedDocuments; 
        }
    }
    
    /**
     * 语义搜索结果
     */
    class SemanticSearchResult {
        private String documentId;
        private String title;
        private String snippet;
        private double relevanceScore;
        private Map<String, Object> metadata;
        
        public SemanticSearchResult(String documentId, String title, String snippet, 
                                  double relevanceScore, Map<String, Object> metadata) {
            this.documentId = documentId;
            this.title = title;
            this.snippet = snippet;
            this.relevanceScore = relevanceScore;
            this.metadata = metadata;
        }
        
        // Getters and Setters
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
        
        public double getRelevanceScore() { return relevanceScore; }
        public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 分类结果
     */
    class ClassificationResult {
        private String primaryCategory;
        private List<String> secondaryCategories;
        private List<String> tags;
        private double confidence;
        private Map<String, Double> categoryScores;
        
        public ClassificationResult(String primaryCategory, List<String> secondaryCategories, 
                                  List<String> tags, double confidence, Map<String, Double> categoryScores) {
            this.primaryCategory = primaryCategory;
            this.secondaryCategories = secondaryCategories;
            this.tags = tags;
            this.confidence = confidence;
            this.categoryScores = categoryScores;
        }
        
        // Getters and Setters
        public String getPrimaryCategory() { return primaryCategory; }
        public void setPrimaryCategory(String primaryCategory) { this.primaryCategory = primaryCategory; }
        
        public List<String> getSecondaryCategories() { return secondaryCategories; }
        public void setSecondaryCategories(List<String> secondaryCategories) { this.secondaryCategories = secondaryCategories; }
        
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public Map<String, Double> getCategoryScores() { return categoryScores; }
        public void setCategoryScores(Map<String, Double> categoryScores) { this.categoryScores = categoryScores; }
    }
    
    /**
     * 相关性分析结果
     */
    class RelevanceAnalysis {
        private double similarityScore;
        private String analysisType;
        private List<String> commonTopics;
        private List<String> differences;
        private String summary;
        
        public RelevanceAnalysis(double similarityScore, String analysisType, List<String> commonTopics, 
                               List<String> differences, String summary) {
            this.similarityScore = similarityScore;
            this.analysisType = analysisType;
            this.commonTopics = commonTopics;
            this.differences = differences;
            this.summary = summary;
        }
        
        // Getters and Setters
        public double getSimilarityScore() { return similarityScore; }
        public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }
        
        public String getAnalysisType() { return analysisType; }
        public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }
        
        public List<String> getCommonTopics() { return commonTopics; }
        public void setCommonTopics(List<String> commonTopics) { this.commonTopics = commonTopics; }
        
        public List<String> getDifferences() { return differences; }
        public void setDifferences(List<String> differences) { this.differences = differences; }
        
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
} 