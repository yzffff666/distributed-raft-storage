package com.github.raftimpl.raft.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异常预测结果DTO
 */
@Schema(description = "异常预测结果")
public class AnomalyPrediction {
    
    @Schema(description = "预测ID", example = "pred_20240110_001")
    private String predictionId;
    
    @Schema(description = "指标名称", example = "cpu_usage")
    private String metricName;
    
    @Schema(description = "预测时间范围(分钟)", example = "60")
    private int forecastMinutes;
    
    @Schema(description = "异常发生概率", example = "0.75")
    private double anomalyProbability;
    
    @Schema(description = "预测置信度", example = "0.85")
    private double confidence;
    
    @Schema(description = "预测的异常类型", example = "THRESHOLD_EXCEEDED")
    private String predictedAnomalyType;
    
    @Schema(description = "预测的异常严重程度", example = "HIGH")
    private String predictedSeverity;
    
    @Schema(description = "预计异常发生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predictedAnomalyTime;
    
    @Schema(description = "预测创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predictionTime;
    
    @Schema(description = "预测值序列")
    private List<PredictionPoint> forecastValues;
    
    @Schema(description = "异常触发条件")
    private Map<String, Object> triggerConditions;
    
    @Schema(description = "预防建议")
    private List<String> preventionRecommendations;
    
    @Schema(description = "相关指标预测")
    private Map<String, Double> relatedMetricsPrediction;
    
    @Schema(description = "模型信息")
    private ModelInfo modelInfo;
    
    @Schema(description = "预测状态", example = "ACTIVE", allowableValues = {"ACTIVE", "EXPIRED", "VALIDATED", "INVALIDATED"})
    private String status;
    
    // 内部类：预测点
    @Schema(description = "预测数据点")
    public static class PredictionPoint {
        @Schema(description = "时间点")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime timestamp;
        
        @Schema(description = "预测值", example = "85.5")
        private double predictedValue;
        
        @Schema(description = "预测区间下界", example = "80.0")
        private double lowerBound;
        
        @Schema(description = "预测区间上界", example = "90.0")
        private double upperBound;
        
        @Schema(description = "异常概率", example = "0.3")
        private double anomalyProbability;
        
        // 构造函数
        public PredictionPoint() {}
        
        public PredictionPoint(LocalDateTime timestamp, double predictedValue) {
            this.timestamp = timestamp;
            this.predictedValue = predictedValue;
        }
        
        // Getters and Setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public double getPredictedValue() { return predictedValue; }
        public void setPredictedValue(double predictedValue) { this.predictedValue = predictedValue; }
        
        public double getLowerBound() { return lowerBound; }
        public void setLowerBound(double lowerBound) { this.lowerBound = lowerBound; }
        
        public double getUpperBound() { return upperBound; }
        public void setUpperBound(double upperBound) { this.upperBound = upperBound; }
        
        public double getAnomalyProbability() { return anomalyProbability; }
        public void setAnomalyProbability(double anomalyProbability) { this.anomalyProbability = anomalyProbability; }
    }
    
    // 内部类：模型信息
    @Schema(description = "预测模型信息")
    public static class ModelInfo {
        @Schema(description = "模型名称", example = "LSTM_ANOMALY_DETECTOR")
        private String modelName;
        
        @Schema(description = "模型版本", example = "v1.2.0")
        private String modelVersion;
        
        @Schema(description = "训练数据大小", example = "10000")
        private int trainingDataSize;
        
        @Schema(description = "模型准确率", example = "0.92")
        private double accuracy;
        
        @Schema(description = "最后训练时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastTrainingTime;
        
        // Getters and Setters
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        
        public String getModelVersion() { return modelVersion; }
        public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
        
        public int getTrainingDataSize() { return trainingDataSize; }
        public void setTrainingDataSize(int trainingDataSize) { this.trainingDataSize = trainingDataSize; }
        
        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
        
        public LocalDateTime getLastTrainingTime() { return lastTrainingTime; }
        public void setLastTrainingTime(LocalDateTime lastTrainingTime) { this.lastTrainingTime = lastTrainingTime; }
    }
    
    // 构造函数
    public AnomalyPrediction() {}
    
    public AnomalyPrediction(String predictionId, String metricName, int forecastMinutes) {
        this.predictionId = predictionId;
        this.metricName = metricName;
        this.forecastMinutes = forecastMinutes;
        this.predictionTime = LocalDateTime.now();
        this.status = "ACTIVE";
    }
    
    // Getters and Setters
    public String getPredictionId() { return predictionId; }
    public void setPredictionId(String predictionId) { this.predictionId = predictionId; }
    
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    
    public int getForecastMinutes() { return forecastMinutes; }
    public void setForecastMinutes(int forecastMinutes) { this.forecastMinutes = forecastMinutes; }
    
    public double getAnomalyProbability() { return anomalyProbability; }
    public void setAnomalyProbability(double anomalyProbability) { this.anomalyProbability = anomalyProbability; }
    
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    
    public String getPredictedAnomalyType() { return predictedAnomalyType; }
    public void setPredictedAnomalyType(String predictedAnomalyType) { this.predictedAnomalyType = predictedAnomalyType; }
    
    public String getPredictedSeverity() { return predictedSeverity; }
    public void setPredictedSeverity(String predictedSeverity) { this.predictedSeverity = predictedSeverity; }
    
    public LocalDateTime getPredictedAnomalyTime() { return predictedAnomalyTime; }
    public void setPredictedAnomalyTime(LocalDateTime predictedAnomalyTime) { this.predictedAnomalyTime = predictedAnomalyTime; }
    
    public LocalDateTime getPredictionTime() { return predictionTime; }
    public void setPredictionTime(LocalDateTime predictionTime) { this.predictionTime = predictionTime; }
    
    public List<PredictionPoint> getForecastValues() { return forecastValues; }
    public void setForecastValues(List<PredictionPoint> forecastValues) { this.forecastValues = forecastValues; }
    
    public Map<String, Object> getTriggerConditions() { return triggerConditions; }
    public void setTriggerConditions(Map<String, Object> triggerConditions) { this.triggerConditions = triggerConditions; }
    
    public List<String> getPreventionRecommendations() { return preventionRecommendations; }
    public void setPreventionRecommendations(List<String> preventionRecommendations) { this.preventionRecommendations = preventionRecommendations; }
    
    public Map<String, Double> getRelatedMetricsPrediction() { return relatedMetricsPrediction; }
    public void setRelatedMetricsPrediction(Map<String, Double> relatedMetricsPrediction) { this.relatedMetricsPrediction = relatedMetricsPrediction; }
    
    public ModelInfo getModelInfo() { return modelInfo; }
    public void setModelInfo(ModelInfo modelInfo) { this.modelInfo = modelInfo; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
} 