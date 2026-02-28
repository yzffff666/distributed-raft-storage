package com.github.raftimpl.raft.api.service;

import com.github.raftimpl.raft.api.dto.AnomalyReport;
import com.github.raftimpl.raft.api.dto.MetricData;
import com.github.raftimpl.raft.api.dto.RootCauseAnalysis;
import com.github.raftimpl.raft.api.dto.AnomalyPrediction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 智能异常检测服务接口
 * 提供基于机器学习的异常检测、根因分析和预测功能
 */
public interface AnomalyDetectionService {
    
    /**
     * 检测系统异常
     * @param metrics 指标数据
     * @param timeWindow 时间窗口（分钟）
     * @return 异常报告列表
     */
    List<AnomalyReport> detectAnomalies(List<MetricData> metrics, int timeWindow);
    
    /**
     * 实时异常检测
     * @param metricName 指标名称
     * @param value 当前值
     * @param timestamp 时间戳
     * @return 是否异常
     */
    boolean isAnomaly(String metricName, double value, LocalDateTime timestamp);
    
    /**
     * 根因分析
     * @param anomalyId 异常ID
     * @return 根因分析结果
     */
    RootCauseAnalysis analyzeRootCause(String anomalyId);
    
    /**
     * 批量根因分析
     * @param anomalies 异常报告列表
     * @return 根因分析结果映射
     */
    Map<String, RootCauseAnalysis> batchAnalyzeRootCause(List<AnomalyReport> anomalies);
    
    /**
     * 异常预测
     * @param metricName 指标名称
     * @param forecastMinutes 预测时间（分钟）
     * @return 异常预测结果
     */
    AnomalyPrediction predictAnomaly(String metricName, int forecastMinutes);
    
    /**
     * 训练异常检测模型
     * @param historicalData 历史数据
     * @return 训练结果
     */
    boolean trainModel(List<MetricData> historicalData);
    
    /**
     * 获取异常检测统计
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息
     */
    Map<String, Object> getAnomalyStatistics(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 获取模型健康状态
     * @return 模型状态信息
     */
    Map<String, Object> getModelHealth();
    
    /**
     * 更新异常阈值
     * @param metricName 指标名称
     * @param threshold 新阈值
     */
    void updateThreshold(String metricName, double threshold);
    
    /**
     * 获取异常趋势分析
     * @param days 分析天数
     * @return 趋势分析结果
     */
    Map<String, Object> getAnomalyTrends(int days);
} 