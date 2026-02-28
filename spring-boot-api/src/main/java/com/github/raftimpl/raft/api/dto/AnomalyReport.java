package com.github.raftimpl.raft.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异常报告DTO
 */
@Schema(description = "异常报告")
public class AnomalyReport {
    
    @Schema(description = "异常ID", example = "anomaly_20240110_001")
    private String anomalyId;
    
    @Schema(description = "指标名称", example = "cpu_usage")
    private String metricName;
    
    @Schema(description = "异常值", example = "95.5")
    private double anomalyValue;
    
    @Schema(description = "正常范围最小值", example = "0.0")
    private double normalMin;
    
    @Schema(description = "正常范围最大值", example = "80.0")
    private double normalMax;
    
    @Schema(description = "异常严重程度", example = "HIGH", allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    private String severity;
    
    @Schema(description = "异常类型", example = "THRESHOLD_EXCEEDED")
    private String anomalyType;
    
    @Schema(description = "异常描述", example = "CPU使用率超过正常阈值")
    private String description;
    
    @Schema(description = "检测时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime detectedAt;
    
    @Schema(description = "异常开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @Schema(description = "异常结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    
    @Schema(description = "置信度", example = "0.95")
    private double confidence;
    
    @Schema(description = "影响的服务列表")
    private List<String> affectedServices;
    
    @Schema(description = "相关指标")
    private Map<String, Double> relatedMetrics;
    
    @Schema(description = "异常状态", example = "ACTIVE", allowableValues = {"ACTIVE", "RESOLVED", "ACKNOWLEDGED"})
    private String status;
    
    @Schema(description = "处理建议")
    private List<String> recommendations;
    
    @Schema(description = "标签")
    private Map<String, String> tags;
    
    // 构造函数
    public AnomalyReport() {}
    
    public AnomalyReport(String anomalyId, String metricName, double anomalyValue, String severity) {
        this.anomalyId = anomalyId;
        this.metricName = metricName;
        this.anomalyValue = anomalyValue;
        this.severity = severity;
        this.detectedAt = LocalDateTime.now();
        this.status = "ACTIVE";
    }
    
    // Getters and Setters
    public String getAnomalyId() { return anomalyId; }
    public void setAnomalyId(String anomalyId) { this.anomalyId = anomalyId; }
    
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    
    public double getAnomalyValue() { return anomalyValue; }
    public void setAnomalyValue(double anomalyValue) { this.anomalyValue = anomalyValue; }
    
    public double getNormalMin() { return normalMin; }
    public void setNormalMin(double normalMin) { this.normalMin = normalMin; }
    
    public double getNormalMax() { return normalMax; }
    public void setNormalMax(double normalMax) { this.normalMax = normalMax; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    
    public String getAnomalyType() { return anomalyType; }
    public void setAnomalyType(String anomalyType) { this.anomalyType = anomalyType; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public List<String> getAffectedServices() { return affectedServices; }
    public void setAffectedServices(List<String> affectedServices) { this.affectedServices = affectedServices; }
    
    public Map<String, Double> getRelatedMetrics() { return relatedMetrics; }
    public void setRelatedMetrics(Map<String, Double> relatedMetrics) { this.relatedMetrics = relatedMetrics; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
} 