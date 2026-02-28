package com.github.raftimpl.raft.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 指标数据DTO
 */
@Schema(description = "指标数据")
public class MetricData {
    
    @Schema(description = "指标名称", example = "cpu_usage")
    private String metricName;
    
    @Schema(description = "指标值", example = "75.5")
    private double value;
    
    @Schema(description = "时间戳")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @Schema(description = "指标单位", example = "%")
    private String unit;
    
    @Schema(description = "数据来源", example = "prometheus")
    private String source;
    
    @Schema(description = "标签")
    private Map<String, String> labels;
    
    @Schema(description = "指标类型", example = "gauge", allowableValues = {"counter", "gauge", "histogram", "summary"})
    private String metricType;
    
    @Schema(description = "采样间隔(秒)", example = "60")
    private int interval;
    
    // 构造函数
    public MetricData() {}
    
    public MetricData(String metricName, double value) {
        this.metricName = metricName;
        this.value = value;
        this.timestamp = LocalDateTime.now();
    }
    
    public MetricData(String metricName, double value, LocalDateTime timestamp) {
        this.metricName = metricName;
        this.value = value;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    
    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }
    
    @Override
    public String toString() {
        return String.format("MetricData{metricName='%s', value=%f, timestamp=%s}", 
            metricName, value, timestamp);
    }
} 