package com.github.raftimpl.raft.api.service.ai;

import com.github.raftimpl.raft.api.dto.ApiResponse;

/**
 * AI服务接口
 * 提供智能分析、自然语言处理等AI能力
 */
public interface AIService {
    
    /**
     * 处理自然语言查询
     * @param query 用户查询
     * @param userId 用户ID
     * @return AI响应
     */
    ApiResponse<String> processQuery(String query, String userId);
    
    /**
     * 分析系统状态并生成报告
     * @return 系统分析报告
     */
    ApiResponse<String> analyzeSystemStatus();
    
    /**
     * 生成数据摘要
     * @param dataKey 数据键
     * @return 数据摘要
     */
    ApiResponse<String> generateDataSummary(String dataKey);
    
    /**
     * 智能推荐优化建议
     * @return 优化建议
     */
    ApiResponse<String> getOptimizationSuggestions();
    
    /**
     * 检查AI服务健康状态
     * @return 健康状态
     */
    ApiResponse<Boolean> checkHealth();
}
