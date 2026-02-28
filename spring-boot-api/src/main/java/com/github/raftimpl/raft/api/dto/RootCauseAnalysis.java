package com.github.raftimpl.raft.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 根因分析结果DTO
 */
@Schema(description = "根因分析结果")
public class RootCauseAnalysis {
    
    @Schema(description = "分析ID", example = "rca_20240110_001")
    private String analysisId;
    
    @Schema(description = "异常ID", example = "anomaly_20240110_001")
    private String anomalyId;
    
    @Schema(description = "根本原因", example = "内存泄漏导致系统性能下降")
    private String rootCause;
    
    @Schema(description = "置信度", example = "0.85")
    private double confidence;
    
    @Schema(description = "可能原因列表")
    private List<String> possibleCauses;
    
    @Schema(description = "解决方案建议")
    private List<String> solutions;
    
    @Schema(description = "预防措施")
    private List<String> preventiveMeasures;
    
    @Schema(description = "分析时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analysisTime;
    
    @Schema(description = "分析耗时(毫秒)", example = "1500")
    private long analysisTimeMs;
    
    @Schema(description = "分析方法", example = "CORRELATION_ANALYSIS")
    private String analysisMethod;
    
    @Schema(description = "分析状态", example = "COMPLETED", allowableValues = {"PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"})
    private String status;
    
    @Schema(description = "相关指标分析")
    private Map<String, Object> metricAnalyses;
    
    // 构造函数
    public RootCauseAnalysis() {}
    
    public RootCauseAnalysis(String analysisId, String anomalyId) {
        this.analysisId = analysisId;
        this.anomalyId = anomalyId;
        this.analysisTime = LocalDateTime.now();
        this.status = "PENDING";
    }
    
    // Getters and Setters
    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
    
    public String getAnomalyId() { return anomalyId; }
    public void setAnomalyId(String anomalyId) { this.anomalyId = anomalyId; }
    
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public List<String> getPossibleCauses() { return possibleCauses; }
    public void setPossibleCauses(List<String> possibleCauses) { this.possibleCauses = possibleCauses; }
    
    public List<String> getSolutions() { return solutions; }
    public void setSolutions(List<String> solutions) { this.solutions = solutions; }
    
    public List<String> getPreventiveMeasures() { return preventiveMeasures; }
    public void setPreventiveMeasures(List<String> preventiveMeasures) { this.preventiveMeasures = preventiveMeasures; }
    
    public LocalDateTime getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(LocalDateTime analysisTime) { this.analysisTime = analysisTime; }
    
    public long getAnalysisTimeMs() { return analysisTimeMs; }
    public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }
    
    public String getAnalysisMethod() { return analysisMethod; }
    public void setAnalysisMethod(String analysisMethod) { this.analysisMethod = analysisMethod; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Map<String, Object> getMetricAnalyses() { return metricAnalyses; }
    public void setMetricAnalyses(Map<String, Object> metricAnalyses) { this.metricAnalyses = metricAnalyses; }
} 