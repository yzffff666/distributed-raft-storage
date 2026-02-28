package com.github.raftimpl.raft.api.controller;

import com.github.raftimpl.raft.api.dto.*;
import com.github.raftimpl.raft.api.service.AnomalyDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 智能异常检测控制器
 * 提供异常检测、根因分析、预测等功能的API接口
 */
@RestController
@RequestMapping("/api/v1/anomaly")
@Tag(name = "异常检测", description = "智能异常检测和根因分析API")
public class AnomalyDetectionController {
    
    @Autowired
    private AnomalyDetectionService anomalyDetectionService;
    
    @PostMapping("/detect")
    @Operation(summary = "检测异常", description = "基于输入的指标数据检测系统异常")
    public ResponseEntity<ApiResponse<List<AnomalyReport>>> detectAnomalies(
            @Valid @RequestBody DetectAnomaliesRequest request) {
        
        try {
            List<AnomalyReport> anomalies = anomalyDetectionService.detectAnomalies(
                request.getMetrics(), request.getTimeWindow());
            
            return ResponseEntity.ok(ApiResponse.success(anomalies));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("异常检测失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/check/{metricName}")
    @Operation(summary = "实时异常检测", description = "检查指定指标的单个值是否异常")
    public ResponseEntity<ApiResponse<AnomalyCheckResponse>> checkAnomaly(
            @Parameter(description = "指标名称") @PathVariable String metricName,
            @Parameter(description = "指标值") @RequestParam double value,
            @Parameter(description = "时间戳") @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp) {
        
        try {
            if (timestamp == null) {
                timestamp = LocalDateTime.now();
            }
            
            boolean isAnomaly = anomalyDetectionService.isAnomaly(metricName, value, timestamp);
            
            AnomalyCheckResponse response = new AnomalyCheckResponse();
            response.setMetricName(metricName);
            response.setValue(value);
            response.setTimestamp(timestamp);
            response.setIsAnomaly(isAnomaly);
            response.setCheckTime(LocalDateTime.now());
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("异常检查失败: " + e.getMessage()));
        }
    }
    
    @PostMapping("/analyze/{anomalyId}")
    @Operation(summary = "根因分析", description = "对指定异常进行根因分析")
    public ResponseEntity<ApiResponse<RootCauseAnalysis>> analyzeRootCause(
            @Parameter(description = "异常ID") @PathVariable String anomalyId) {
        
        try {
            RootCauseAnalysis analysis = anomalyDetectionService.analyzeRootCause(anomalyId);
            
            if (analysis == null) {
                return ResponseEntity.ok(ApiResponse.error("未找到指定的异常报告"));
            }
            
            return ResponseEntity.ok(ApiResponse.success(analysis));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("根因分析失败: " + e.getMessage()));
        }
    }
    
    @PostMapping("/analyze/batch")
    @Operation(summary = "批量根因分析", description = "对多个异常进行批量根因分析")
    public ResponseEntity<ApiResponse<Map<String, RootCauseAnalysis>>> batchAnalyzeRootCause(
            @Valid @RequestBody BatchAnalysisRequest request) {
        
        try {
            Map<String, RootCauseAnalysis> analyses = anomalyDetectionService.batchAnalyzeRootCause(
                request.getAnomalies());
            
            return ResponseEntity.ok(ApiResponse.success(analyses));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("批量根因分析失败: " + e.getMessage()));
        }
    }
    
    @PostMapping("/predict/{metricName}")
    @Operation(summary = "异常预测", description = "预测指定指标在未来时间段内的异常情况")
    public ResponseEntity<ApiResponse<AnomalyPrediction>> predictAnomaly(
            @Parameter(description = "指标名称") @PathVariable String metricName,
            @Parameter(description = "预测时间(分钟)") @RequestParam(defaultValue = "60") int forecastMinutes) {
        
        try {
            AnomalyPrediction prediction = anomalyDetectionService.predictAnomaly(metricName, forecastMinutes);
            
            return ResponseEntity.ok(ApiResponse.success(prediction));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("异常预测失败: " + e.getMessage()));
        }
    }
    
    @PostMapping("/train")
    @Operation(summary = "训练模型", description = "使用历史数据训练异常检测模型")
    public ResponseEntity<ApiResponse<TrainingResponse>> trainModel(
            @Valid @RequestBody TrainingRequest request) {
        
        try {
            boolean success = anomalyDetectionService.trainModel(request.getHistoricalData());
            
            TrainingResponse response = new TrainingResponse();
            response.setSuccess(success);
            response.setTrainingTime(LocalDateTime.now());
            response.setDataSize(request.getHistoricalData().size());
            response.setMessage(success ? "模型训练成功" : "模型训练失败");
            
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("模型训练失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/statistics")
    @Operation(summary = "异常统计", description = "获取指定时间段内的异常统计信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnomalyStatistics(
            @Parameter(description = "开始时间") @RequestParam 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        try {
            Map<String, Object> statistics = anomalyDetectionService.getAnomalyStatistics(startTime, endTime);
            
            return ResponseEntity.ok(ApiResponse.success(statistics));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取异常统计失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/model/health")
    @Operation(summary = "模型健康状态", description = "获取异常检测模型的健康状态信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getModelHealth() {
        
        try {
            Map<String, Object> health = anomalyDetectionService.getModelHealth();
            
            return ResponseEntity.ok(ApiResponse.success(health));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取模型健康状态失败: " + e.getMessage()));
        }
    }
    
    @PutMapping("/threshold/{metricName}")
    @Operation(summary = "更新阈值", description = "更新指定指标的异常检测阈值")
    public ResponseEntity<ApiResponse<String>> updateThreshold(
            @Parameter(description = "指标名称") @PathVariable String metricName,
            @Parameter(description = "新阈值") @RequestParam double threshold) {
        
        try {
            anomalyDetectionService.updateThreshold(metricName, threshold);
            
            return ResponseEntity.ok(ApiResponse.success("阈值更新成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("阈值更新失败: " + e.getMessage()));
        }
    }
    
    @GetMapping("/trends")
    @Operation(summary = "异常趋势分析", description = "获取指定天数内的异常趋势分析")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnomalyTrends(
            @Parameter(description = "分析天数") @RequestParam(defaultValue = "7") int days) {
        
        try {
            Map<String, Object> trends = anomalyDetectionService.getAnomalyTrends(days);
            
            return ResponseEntity.ok(ApiResponse.success(trends));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取异常趋势失败: " + e.getMessage()));
        }
    }
    
    // 内部DTO类
    public static class DetectAnomaliesRequest {
        private List<MetricData> metrics;
        private int timeWindow = 60; // 默认60分钟
        
        public List<MetricData> getMetrics() { return metrics; }
        public void setMetrics(List<MetricData> metrics) { this.metrics = metrics; }
        public int getTimeWindow() { return timeWindow; }
        public void setTimeWindow(int timeWindow) { this.timeWindow = timeWindow; }
    }
    
    public static class BatchAnalysisRequest {
        private List<AnomalyReport> anomalies;
        
        public List<AnomalyReport> getAnomalies() { return anomalies; }
        public void setAnomalies(List<AnomalyReport> anomalies) { this.anomalies = anomalies; }
    }
    
    public static class TrainingRequest {
        private List<MetricData> historicalData;
        
        public List<MetricData> getHistoricalData() { return historicalData; }
        public void setHistoricalData(List<MetricData> historicalData) { this.historicalData = historicalData; }
    }
    
    public static class AnomalyCheckResponse {
        private String metricName;
        private double value;
        private LocalDateTime timestamp;
        private boolean isAnomaly;
        private LocalDateTime checkTime;
        
        // Getters and Setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public boolean getIsAnomaly() { return isAnomaly; }
        public void setIsAnomaly(boolean isAnomaly) { this.isAnomaly = isAnomaly; }
        public LocalDateTime getCheckTime() { return checkTime; }
        public void setCheckTime(LocalDateTime checkTime) { this.checkTime = checkTime; }
    }
    
    public static class TrainingResponse {
        private boolean success;
        private LocalDateTime trainingTime;
        private int dataSize;
        private String message;
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public LocalDateTime getTrainingTime() { return trainingTime; }
        public void setTrainingTime(LocalDateTime trainingTime) { this.trainingTime = trainingTime; }
        public int getDataSize() { return dataSize; }
        public void setDataSize(int dataSize) { this.dataSize = dataSize; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
} 