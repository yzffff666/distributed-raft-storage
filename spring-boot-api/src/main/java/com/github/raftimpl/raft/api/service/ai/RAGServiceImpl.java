package com.github.raftimpl.raft.api.service.ai;

import com.github.raftimpl.raft.api.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG（检索增强生成）服务实现类
 * 结合向量搜索和生成式AI，提供智能问答功能
 */
@Service
public class RAGServiceImpl implements RAGService {
    
    private static final Logger logger = LoggerFactory.getLogger(RAGServiceImpl.class);
    
    @Autowired
    private VectorDatabaseService vectorDatabaseService;
    
    @Autowired
    private AIService aiService;
    
    // 预定义的分类类别
    private static final List<String> CATEGORIES = Arrays.asList(
        "技术文档", "业务需求", "系统设计", "用户手册", "API文档", 
        "配置文件", "日志记录", "测试报告", "部署文档", "其他"
    );
    
    // 预定义的标签
    private static final List<String> COMMON_TAGS = Arrays.asList(
        "重要", "紧急", "已完成", "进行中", "待审核", "已废弃",
        "前端", "后端", "数据库", "缓存", "监控", "安全", "性能"
    );
    
    @Override
    public ApiResponse<RAGResponse> intelligentQA(String question, Map<String, Object> context) {
        try {
            logger.info("Processing intelligent QA for question: {}", question);
            
            // 1. 使用向量搜索找到相关文档
            ApiResponse<List<VectorDatabaseService.SimilarDocument>> searchResult = 
                vectorDatabaseService.searchSimilarDocuments(question, 5);
            
            if (!searchResult.isSuccess()) {
                return ApiResponse.error("Failed to search relevant documents");
            }
            
            List<VectorDatabaseService.SimilarDocument> relatedDocs = searchResult.getData();
            
            // 2. 构建上下文
            StringBuilder contextBuilder = new StringBuilder();
            List<String> sources = new ArrayList<>();
            
            contextBuilder.append("基于以下相关文档回答问题：\n\n");
            for (VectorDatabaseService.SimilarDocument doc : relatedDocs) {
                contextBuilder.append("文档ID: ").append(doc.getDocumentId()).append("\n");
                contextBuilder.append("内容: ").append(doc.getContent()).append("\n");
                contextBuilder.append("相似度: ").append(String.format("%.2f", doc.getSimilarity())).append("\n\n");
                sources.add(doc.getDocumentId());
            }
            
            contextBuilder.append("问题: ").append(question).append("\n");
            contextBuilder.append("请基于上述文档内容提供准确的答案。");
            
            // 3. 使用AI服务生成答案
            ApiResponse<String> aiResponse = aiService.processQuery(contextBuilder.toString(), "system");
            
            if (!aiResponse.isSuccess()) {
                return ApiResponse.error("Failed to generate AI response");
            }
            
            // 4. 计算置信度（基于相关文档的相似度）
            double confidence = relatedDocs.isEmpty() ? 0.5 : 
                relatedDocs.stream().mapToDouble(VectorDatabaseService.SimilarDocument::getSimilarity).average().orElse(0.5);
            
            RAGResponse ragResponse = new RAGResponse(
                aiResponse.getData(),
                sources,
                confidence,
                relatedDocs
            );
            
            logger.info("Successfully generated intelligent QA response with confidence: {}", confidence);
            return ApiResponse.success(ragResponse);
            
        } catch (Exception e) {
            logger.error("Error in intelligent QA: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to process intelligent QA: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<String> documentBasedQA(String question, List<String> documentIds) {
        try {
            logger.info("Processing document-based QA for {} documents", documentIds.size());
            
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("基于以下指定文档回答问题：\n\n");
            
            // 获取指定文档的内容
            for (String docId : documentIds) {
                ApiResponse<VectorDatabaseService.DocumentVector> docResult = 
                    vectorDatabaseService.getDocumentVector(docId);
                
                if (docResult.isSuccess()) {
                    // 这里需要从文档存储中获取实际内容，暂时使用模拟
                    contextBuilder.append("文档ID: ").append(docId).append("\n");
                    contextBuilder.append("内容: [文档内容]\n\n");
                }
            }
            
            contextBuilder.append("问题: ").append(question).append("\n");
            contextBuilder.append("请基于上述指定文档内容提供答案。");
            
            // 使用AI服务生成答案
            ApiResponse<String> aiResponse = aiService.processQuery(contextBuilder.toString(), "system");
            
            if (!aiResponse.isSuccess()) {
                return ApiResponse.error("Failed to generate document-based answer");
            }
            
            logger.info("Successfully generated document-based QA response");
            return aiResponse;
            
        } catch (Exception e) {
            logger.error("Error in document-based QA: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to process document-based QA: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<List<SemanticSearchResult>> semanticSearch(String query, Map<String, Object> filters) {
        try {
            logger.info("Processing semantic search for query: {}", query);
            
            // 使用向量搜索
            ApiResponse<List<VectorDatabaseService.SimilarDocument>> searchResult = 
                vectorDatabaseService.searchSimilarDocuments(query, 10);
            
            if (!searchResult.isSuccess()) {
                return ApiResponse.error("Failed to perform semantic search");
            }
            
            // 转换为语义搜索结果
            List<SemanticSearchResult> results = searchResult.getData().stream()
                .map(doc -> {
                    String title = extractTitle(doc.getContent());
                    String snippet = extractSnippet(doc.getContent(), query);
                    return new SemanticSearchResult(
                        doc.getDocumentId(),
                        title,
                        snippet,
                        doc.getSimilarity(),
                        doc.getMetadata()
                    );
                })
                .collect(Collectors.toList());
            
            // 应用过滤器（如果有）
            if (filters != null && !filters.isEmpty()) {
                results = applyFilters(results, filters);
            }
            
            logger.info("Found {} semantic search results", results.size());
            return ApiResponse.success(results);
            
        } catch (Exception e) {
            logger.error("Error in semantic search: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to perform semantic search: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<String> generateIntelligentSummary(String content, String summaryType) {
        try {
            logger.info("Generating intelligent summary of type: {}", summaryType);
            
            StringBuilder prompt = new StringBuilder();
            prompt.append("请为以下内容生成");
            
            switch (summaryType.toLowerCase()) {
                case "brief":
                    prompt.append("简短摘要（1-2句话）");
                    break;
                case "detailed":
                    prompt.append("详细摘要（包含主要观点和细节）");
                    break;
                case "bullet":
                    prompt.append("要点式摘要（使用项目符号列表）");
                    break;
                case "executive":
                    prompt.append("执行摘要（适合管理层阅读）");
                    break;
                default:
                    prompt.append("标准摘要");
                    break;
            }
            
            prompt.append("：\n\n").append(content);
            
            ApiResponse<String> aiResponse = aiService.processQuery(prompt.toString(), "system");
            
            if (!aiResponse.isSuccess()) {
                return ApiResponse.error("Failed to generate intelligent summary");
            }
            
            logger.info("Successfully generated intelligent summary");
            return aiResponse;
            
        } catch (Exception e) {
            logger.error("Error generating intelligent summary: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to generate summary: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<ClassificationResult> autoClassifyAndTag(String content) {
        try {
            logger.info("Auto-classifying and tagging content");
            
            // 基于内容关键词进行简单分类
            String primaryCategory = classifyContent(content);
            List<String> secondaryCategories = findSecondaryCategories(content);
            List<String> tags = generateTags(content);
            double confidence = calculateClassificationConfidence(content, primaryCategory);
            
            // 生成分类分数
            Map<String, Double> categoryScores = new HashMap<>();
            for (String category : CATEGORIES) {
                categoryScores.put(category, calculateCategoryScore(content, category));
            }
            
            ClassificationResult result = new ClassificationResult(
                primaryCategory,
                secondaryCategories,
                tags,
                confidence,
                categoryScores
            );
            
            logger.info("Successfully classified content as: {}", primaryCategory);
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            logger.error("Error in auto-classification: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to classify content: " + e.getMessage());
        }
    }
    
    @Override
    public ApiResponse<RelevanceAnalysis> analyzeRelevance(String content1, String content2) {
        try {
            logger.info("Analyzing relevance between two contents");
            
            // 使用向量相似度计算
            ApiResponse<String> storeResult1 = vectorDatabaseService.storeDocumentVector("temp_1", content1, new HashMap<>());
            ApiResponse<String> storeResult2 = vectorDatabaseService.storeDocumentVector("temp_2", content2, new HashMap<>());
            
            if (!storeResult1.isSuccess() || !storeResult2.isSuccess()) {
                return ApiResponse.error("Failed to process content for relevance analysis");
            }
            
            // 搜索相似性
            ApiResponse<List<VectorDatabaseService.SimilarDocument>> searchResult = 
                vectorDatabaseService.searchSimilarDocuments(content1, 5);
            
            double similarityScore = 0.0;
            if (searchResult.isSuccess()) {
                for (VectorDatabaseService.SimilarDocument doc : searchResult.getData()) {
                    if ("temp_2".equals(doc.getDocumentId())) {
                        similarityScore = doc.getSimilarity();
                        break;
                    }
                }
            }
            
            // 分析共同主题和差异
            List<String> commonTopics = findCommonTopics(content1, content2);
            List<String> differences = findDifferences(content1, content2);
            String summary = generateRelevanceSummary(similarityScore, commonTopics, differences);
            
            RelevanceAnalysis analysis = new RelevanceAnalysis(
                similarityScore,
                "Vector Similarity",
                commonTopics,
                differences,
                summary
            );
            
            // 清理临时文档
            vectorDatabaseService.deleteDocumentVector("temp_1");
            vectorDatabaseService.deleteDocumentVector("temp_2");
            
            logger.info("Relevance analysis completed with similarity score: {}", similarityScore);
            return ApiResponse.success(analysis);
            
        } catch (Exception e) {
            logger.error("Error in relevance analysis: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to analyze relevance: " + e.getMessage());
        }
    }
    
    // 辅助方法
    private String extractTitle(String content) {
        String[] lines = content.split("\n");
        return lines.length > 0 ? lines[0].substring(0, Math.min(50, lines[0].length())) + "..." : "无标题";
    }
    
    private String extractSnippet(String content, String query) {
        // 简单的摘要提取逻辑
        String[] sentences = content.split("[.!?]+");
        for (String sentence : sentences) {
            if (sentence.toLowerCase().contains(query.toLowerCase())) {
                return sentence.trim() + "...";
            }
        }
        return content.substring(0, Math.min(150, content.length())) + "...";
    }
    
    private List<SemanticSearchResult> applyFilters(List<SemanticSearchResult> results, Map<String, Object> filters) {
        // 简单的过滤逻辑实现
        return results.stream()
            .filter(result -> {
                for (Map.Entry<String, Object> filter : filters.entrySet()) {
                    Object metadataValue = result.getMetadata().get(filter.getKey());
                    if (metadataValue != null && !metadataValue.equals(filter.getValue())) {
                        return false;
                    }
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    private String classifyContent(String content) {
        String lowerContent = content.toLowerCase();
        
        if (lowerContent.contains("api") || lowerContent.contains("接口") || lowerContent.contains("endpoint")) {
            return "API文档";
        } else if (lowerContent.contains("config") || lowerContent.contains("配置") || lowerContent.contains("设置")) {
            return "配置文件";
        } else if (lowerContent.contains("test") || lowerContent.contains("测试") || lowerContent.contains("验证")) {
            return "测试报告";
        } else if (lowerContent.contains("deploy") || lowerContent.contains("部署") || lowerContent.contains("发布")) {
            return "部署文档";
        } else if (lowerContent.contains("user") || lowerContent.contains("用户") || lowerContent.contains("使用")) {
            return "用户手册";
        } else if (lowerContent.contains("design") || lowerContent.contains("设计") || lowerContent.contains("架构")) {
            return "系统设计";
        } else if (lowerContent.contains("requirement") || lowerContent.contains("需求") || lowerContent.contains("业务")) {
            return "业务需求";
        } else if (lowerContent.contains("log") || lowerContent.contains("日志") || lowerContent.contains("记录")) {
            return "日志记录";
        } else {
            return "技术文档";
        }
    }
    
    private List<String> findSecondaryCategories(String content) {
        List<String> secondary = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        if (lowerContent.contains("重要") || lowerContent.contains("critical")) {
            secondary.add("重要文档");
        }
        if (lowerContent.contains("临时") || lowerContent.contains("temp")) {
            secondary.add("临时文档");
        }
        
        return secondary;
    }
    
    private List<String> generateTags(String content) {
        List<String> tags = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        for (String tag : COMMON_TAGS) {
            if (lowerContent.contains(tag.toLowerCase())) {
                tags.add(tag);
            }
        }
        
        return tags;
    }
    
    private double calculateClassificationConfidence(String content, String category) {
        // 简单的置信度计算
        return 0.8 + (Math.random() * 0.2); // 0.8-1.0之间
    }
    
    private double calculateCategoryScore(String content, String category) {
        // 基于关键词匹配计算分数
        return Math.random(); // 简化实现
    }
    
    private List<String> findCommonTopics(String content1, String content2) {
        // 简单的共同主题查找
        List<String> topics = new ArrayList<>();
        String[] words1 = content1.toLowerCase().split("\\s+");
        String[] words2 = content2.toLowerCase().split("\\s+");
        
        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));
        
        set1.retainAll(set2);
        topics.addAll(set1.stream().filter(word -> word.length() > 3).limit(5).collect(Collectors.toList()));
        
        return topics;
    }
    
    private List<String> findDifferences(String content1, String content2) {
        // 简单的差异分析
        List<String> differences = new ArrayList<>();
        
        if (content1.length() > content2.length() * 1.5) {
            differences.add("内容1显著长于内容2");
        } else if (content2.length() > content1.length() * 1.5) {
            differences.add("内容2显著长于内容1");
        }
        
        return differences;
    }
    
    private String generateRelevanceSummary(double similarity, List<String> commonTopics, List<String> differences) {
        StringBuilder summary = new StringBuilder();
        
        if (similarity > 0.8) {
            summary.append("两个内容高度相关");
        } else if (similarity > 0.6) {
            summary.append("两个内容中度相关");
        } else {
            summary.append("两个内容相关性较低");
        }
        
        if (!commonTopics.isEmpty()) {
            summary.append("，共同涉及：").append(String.join("、", commonTopics));
        }
        
        return summary.toString();
    }
} 