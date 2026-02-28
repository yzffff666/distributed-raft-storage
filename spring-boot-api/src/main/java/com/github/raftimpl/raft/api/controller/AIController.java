package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.service.ai.AIService;
import com.github.raftimpl.raft.api.service.ai.RAGService;
import com.github.raftimpl.raft.api.service.ai.VectorDatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * AI服务控制器
 * 提供AI智能分析和自然语言查询接口
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI服务", description = "AI智能分析和自然语言查询")
public class AIController {
    
    private static final Logger logger = LoggerFactory.getLogger(AIController.class);
    
    @Autowired
    private AIService aiService;
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private VectorDatabaseService vectorDatabaseService;
    
    /**
     * 处理自然语言查询
     */
    @PostMapping("/query")
    @Operation(summary = "自然语言查询", description = "处理用户的自然语言查询请求")
    public ApiResponse<String> processQuery(
            @Parameter(description = "查询内容") @RequestBody @Valid QueryRequest request,
            Authentication authentication) {
        
        String userId = authentication != null ? authentication.getName() : "anonymous";
        logger.info("Processing AI query from user: {}", userId);
        
        return aiService.processQuery(request.getQuery(), userId);
    }
    
    /**
     * 智能问答（RAG）
     */
    @PostMapping("/qa")
    @Operation(summary = "智能问答", description = "基于文档库的智能问答")
    public ApiResponse<RAGService.RAGResponse> intelligentQA(
            @Parameter(description = "问答请求") @RequestBody @Valid QARequest request,
            Authentication authentication) {
        
        logger.info("Processing intelligent QA: {}", request.getQuestion());
        return ragService.intelligentQA(request.getQuestion(), request.getContext());
    }
    
    /**
     * 基于文档的问答
     */
    @PostMapping("/qa/documents")
    @Operation(summary = "基于文档的问答", description = "基于指定文档的问答")
    public ApiResponse<String> documentBasedQA(
            @Parameter(description = "文档问答请求") @RequestBody @Valid DocumentQARequest request) {
        
        logger.info("Processing document-based QA for {} documents", request.getDocumentIds().size());
        return ragService.documentBasedQA(request.getQuestion(), request.getDocumentIds());
    }
    
    /**
     * 语义搜索
     */
    @PostMapping("/search")
    @Operation(summary = "语义搜索", description = "基于语义的智能搜索")
    public ApiResponse<List<RAGService.SemanticSearchResult>> semanticSearch(
            @Parameter(description = "搜索请求") @RequestBody @Valid SearchRequest request) {
        
        logger.info("Processing semantic search: {}", request.getQuery());
        return ragService.semanticSearch(request.getQuery(), request.getFilters());
    }
    
    /**
     * 智能摘要生成
     */
    @PostMapping("/summary")
    @Operation(summary = "智能摘要生成", description = "生成内容的智能摘要")
    public ApiResponse<String> generateIntelligentSummary(
            @Parameter(description = "摘要请求") @RequestBody @Valid SummaryRequest request) {
        
        logger.info("Generating intelligent summary of type: {}", request.getSummaryType());
        return ragService.generateIntelligentSummary(request.getContent(), request.getSummaryType());
    }
    
    /**
     * 自动分类和标签生成
     */
    @PostMapping("/classify")
    @Operation(summary = "自动分类和标签生成", description = "自动分类内容并生成标签")
    public ApiResponse<RAGService.ClassificationResult> autoClassifyAndTag(
            @Parameter(description = "分类请求") @RequestBody @Valid ClassificationRequest request) {
        
        logger.info("Auto-classifying content");
        return ragService.autoClassifyAndTag(request.getContent());
    }
    
    /**
     * 相关性分析
     */
    @PostMapping("/relevance")
    @Operation(summary = "相关性分析", description = "分析两个内容的相关性")
    public ApiResponse<RAGService.RelevanceAnalysis> analyzeRelevance(
            @Parameter(description = "相关性分析请求") @RequestBody @Valid RelevanceRequest request) {
        
        logger.info("Analyzing relevance between two contents");
        return ragService.analyzeRelevance(request.getContent1(), request.getContent2());
    }
    
    /**
     * 存储文档向量
     */
    @PostMapping("/vectors/store")
    @Operation(summary = "存储文档向量", description = "将文档转换为向量并存储")
    public ApiResponse<String> storeDocumentVector(
            @Parameter(description = "文档向量请求") @RequestBody @Valid DocumentVectorRequest request) {
        
        logger.info("Storing document vector for ID: {}", request.getDocumentId());
        return vectorDatabaseService.storeDocumentVector(
            request.getDocumentId(), 
            request.getContent(), 
            request.getMetadata()
        );
    }
    
    /**
     * 搜索相似文档
     */
    @PostMapping("/vectors/search")
    @Operation(summary = "搜索相似文档", description = "基于向量相似度搜索文档")
    public ApiResponse<List<VectorDatabaseService.SimilarDocument>> searchSimilarDocuments(
            @Parameter(description = "向量搜索请求") @RequestBody @Valid VectorSearchRequest request) {
        
        logger.info("Searching similar documents for query: {}", request.getQuery());
        return vectorDatabaseService.searchSimilarDocuments(request.getQuery(), request.getTopK());
    }
    
    /**
     * 批量存储文档
     */
    @PostMapping("/vectors/batch")
    @Operation(summary = "批量存储文档", description = "批量存储多个文档的向量")
    public ApiResponse<Integer> batchStoreDocuments(
            @Parameter(description = "批量文档请求") @RequestBody @Valid BatchDocumentRequest request) {
        
        logger.info("Batch storing {} documents", request.getDocuments().size());
        return vectorDatabaseService.batchStoreDocuments(request.getDocuments());
    }
    
    /**
     * 获取向量数据库统计
     */
    @GetMapping("/vectors/stats")
    @Operation(summary = "向量数据库统计", description = "获取向量数据库统计信息")
    public ApiResponse<VectorDatabaseService.VectorDatabaseStats> getVectorStats() {
        logger.info("Getting vector database stats");
        return vectorDatabaseService.getStats();
    }
    
    /**
     * 系统状态分析
     */
    @GetMapping("/analyze/system")
    @Operation(summary = "系统状态分析", description = "生成系统状态智能分析报告")
    public ApiResponse<String> analyzeSystemStatus() {
        logger.info("Generating system status analysis");
        return aiService.analyzeSystemStatus();
    }
    
    /**
     * 数据摘要生成
     */
    @GetMapping("/summary/{dataKey}")
    @Operation(summary = "数据摘要生成", description = "为指定数据键生成智能摘要")
    public ApiResponse<String> generateDataSummary(
            @Parameter(description = "数据键") @PathVariable String dataKey) {
        
        logger.info("Generating data summary for key: {}", dataKey);
        return aiService.generateDataSummary(dataKey);
    }
    
    /**
     * 优化建议
     */
    @GetMapping("/suggestions")
    @Operation(summary = "优化建议", description = "获取系统优化建议")
    public ApiResponse<String> getOptimizationSuggestions() {
        logger.info("Generating optimization suggestions");
        return aiService.getOptimizationSuggestions();
    }
    
    /**
     * AI服务健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "AI服务健康检查", description = "检查AI服务的健康状态")
    public ApiResponse<Boolean> checkHealth() {
        return aiService.checkHealth();
    }
    
    // DTO类定义
    
    /**
     * 查询请求DTO
     */
    public static class QueryRequest {
        @NotBlank(message = "查询内容不能为空")
        private String query;
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }
    
    /**
     * 问答请求DTO
     */
    public static class QARequest {
        @NotBlank(message = "问题不能为空")
        private String question;
        private Map<String, Object> context;
        
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
    }
    
    /**
     * 文档问答请求DTO
     */
    public static class DocumentQARequest {
        @NotBlank(message = "问题不能为空")
        private String question;
        @NotEmpty(message = "文档ID列表不能为空")
        private List<String> documentIds;
        
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public List<String> getDocumentIds() { return documentIds; }
        public void setDocumentIds(List<String> documentIds) { this.documentIds = documentIds; }
    }
    
    /**
     * 搜索请求DTO
     */
    public static class SearchRequest {
        @NotBlank(message = "搜索查询不能为空")
        private String query;
        private Map<String, Object> filters;
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public Map<String, Object> getFilters() { return filters; }
        public void setFilters(Map<String, Object> filters) { this.filters = filters; }
    }
    
    /**
     * 摘要请求DTO
     */
    public static class SummaryRequest {
        @NotBlank(message = "内容不能为空")
        private String content;
        private String summaryType = "standard";
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSummaryType() { return summaryType; }
        public void setSummaryType(String summaryType) { this.summaryType = summaryType; }
    }
    
    /**
     * 分类请求DTO
     */
    public static class ClassificationRequest {
        @NotBlank(message = "内容不能为空")
        private String content;
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
    
    /**
     * 相关性分析请求DTO
     */
    public static class RelevanceRequest {
        @NotBlank(message = "内容1不能为空")
        private String content1;
        @NotBlank(message = "内容2不能为空")
        private String content2;
        
        public String getContent1() { return content1; }
        public void setContent1(String content1) { this.content1 = content1; }
        public String getContent2() { return content2; }
        public void setContent2(String content2) { this.content2 = content2; }
    }
    
    /**
     * 文档向量请求DTO
     */
    public static class DocumentVectorRequest {
        @NotBlank(message = "文档ID不能为空")
        private String documentId;
        @NotBlank(message = "内容不能为空")
        private String content;
        private Map<String, Object> metadata;
        
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 向量搜索请求DTO
     */
    public static class VectorSearchRequest {
        @NotBlank(message = "查询不能为空")
        private String query;
        private int topK = 5;
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }
    
    /**
     * 批量文档请求DTO
     */
    public static class BatchDocumentRequest {
        @NotEmpty(message = "文档列表不能为空")
        private List<VectorDatabaseService.DocumentInfo> documents;
        
        public List<VectorDatabaseService.DocumentInfo> getDocuments() { return documents; }
        public void setDocuments(List<VectorDatabaseService.DocumentInfo> documents) { this.documents = documents; }
    }
}
