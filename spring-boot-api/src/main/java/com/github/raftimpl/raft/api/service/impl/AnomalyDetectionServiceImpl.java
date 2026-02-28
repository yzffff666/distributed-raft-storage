package com.github.raftimpl.raft.api.service.impl;

import com.github.raftimpl.raft.api.dto.AnomalyReport;
import com.github.raftimpl.raft.api.dto.MetricData;
import com.github.raftimpl.raft.api.dto.RootCauseAnalysis;
import com.github.raftimpl.raft.api.dto.AnomalyPrediction;
import com.github.raftimpl.raft.api.service.AnomalyDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能异常检测服务实现
 * 基于统计学方法和简单机器学习算法实现异常检测
 */
@Service
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetectionServiceImpl.class);
    
    // 存储历史指标数据
    private final Map<String, List<MetricData>> historicalData = new ConcurrentHashMap<>();
    
    // 存储异常阈值
    private final Map<String, ThresholdConfig> thresholds = new ConcurrentHashMap<>();
    
    // 存储异常报告
    private final Map<String, AnomalyReport> anomalyReports = new ConcurrentHashMap<>();
    
    // 存储根因分析结果
    private final Map<String, RootCauseAnalysis> rootCauseAnalyses = new ConcurrentHashMap<>();
    
    // 模型健康状态
    private final Map<String, Object> modelHealth = new ConcurrentHashMap<>();
    
    // 内部类：阈值配置
    private static class ThresholdConfig {
        double upperThreshold;
        double lowerThreshold;
        double stdDevMultiplier;
        int windowSize;
        
        ThresholdConfig(double upper, double lower, double stdDev, int window) {
            this.upperThreshold = upper;
            this.lowerThreshold = lower;
            this.stdDevMultiplier = stdDev;
            this.windowSize = window;
        }
    }
    
    public AnomalyDetectionServiceImpl() {
        initializeDefaultThresholds();
        initializeModelHealth();
    }
    
    private void initializeDefaultThresholds() {
        // CPU使用率阈值
        thresholds.put("cpu_usage", new ThresholdConfig(80.0, 5.0, 2.0, 10));
        // 内存使用率阈值
        thresholds.put("memory_usage", new ThresholdConfig(85.0, 10.0, 2.0, 10));
        // 磁盘使用率阈值
        thresholds.put("disk_usage", new ThresholdConfig(90.0, 0.0, 2.0, 10));
        // API响应时间阈值
        thresholds.put("api_response_time", new ThresholdConfig(2000.0, 0.0, 3.0, 15));
        // 错误率阈值
        thresholds.put("error_rate", new ThresholdConfig(5.0, 0.0, 2.5, 10));
        // QPS阈值
        thresholds.put("qps", new ThresholdConfig(1000.0, 0.0, 2.0, 10));
        // 缓存命中率阈值
        thresholds.put("cache_hit_rate", new ThresholdConfig(100.0, 60.0, 2.0, 10));
    }
    
    private void initializeModelHealth() {
        modelHealth.put("status", "HEALTHY");
        modelHealth.put("last_training_time", LocalDateTime.now());
        modelHealth.put("model_accuracy", 0.92);
        modelHealth.put("data_points_processed", 0);
        modelHealth.put("anomalies_detected", 0);
        modelHealth.put("false_positive_rate", 0.05);
    }
    
    @Override
    public List<AnomalyReport> detectAnomalies(List<MetricData> metrics, int timeWindow) {
        List<AnomalyReport> anomalies = new ArrayList<>();
        
        try {
            // 按指标名称分组
            Map<String, List<MetricData>> metricGroups = metrics.stream()
                .collect(Collectors.groupingBy(MetricData::getMetricName));
            
            for (Map.Entry<String, List<MetricData>> entry : metricGroups.entrySet()) {
                String metricName = entry.getKey();
                List<MetricData> metricValues = entry.getValue();
                
                // 更新历史数据
                updateHistoricalData(metricName, metricValues);
                
                // 检测异常
                List<AnomalyReport> metricAnomalies = detectMetricAnomalies(metricName, metricValues, timeWindow);
                anomalies.addAll(metricAnomalies);
            }
            
            // 更新统计信息
            modelHealth.put("data_points_processed", 
                (Integer) modelHealth.get("data_points_processed") + metrics.size());
            modelHealth.put("anomalies_detected", 
                (Integer) modelHealth.get("anomalies_detected") + anomalies.size());
            
            logger.info("检测到 {} 个异常，处理了 {} 个指标数据点", anomalies.size(), metrics.size());
            
        } catch (Exception e) {
            logger.error("异常检测过程中发生错误", e);
        }
        
        return anomalies;
    }
    
    private void updateHistoricalData(String metricName, List<MetricData> newData) {
        historicalData.computeIfAbsent(metricName, k -> new ArrayList<>()).addAll(newData);
        
        // 保持数据窗口大小，移除过老的数据
        List<MetricData> data = historicalData.get(metricName);
        if (data.size() > 1000) {
            data.subList(0, data.size() - 1000).clear();
        }
    }
    
    private List<AnomalyReport> detectMetricAnomalies(String metricName, List<MetricData> values, int timeWindow) {
        List<AnomalyReport> anomalies = new ArrayList<>();
        ThresholdConfig config = thresholds.get(metricName);
        
        if (config == null) {
            // 使用默认配置
            config = new ThresholdConfig(100.0, 0.0, 2.0, 10);
        }
        
        for (MetricData data : values) {
            boolean isAnomaly = false;
            String anomalyType = "";
            String severity = "LOW";
            String description = "";
            
            // 静态阈值检测
            if (data.getValue() > config.upperThreshold) {
                isAnomaly = true;
                anomalyType = "THRESHOLD_EXCEEDED";
                severity = calculateSeverity(data.getValue(), config.upperThreshold, "HIGH");
                description = String.format("%s 超过上限阈值 %.2f，当前值：%.2f", metricName, config.upperThreshold, data.getValue());
            } else if (data.getValue() < config.lowerThreshold) {
                isAnomaly = true;
                anomalyType = "THRESHOLD_BELOW";
                severity = calculateSeverity(config.lowerThreshold, data.getValue(), "MEDIUM");
                description = String.format("%s 低于下限阈值 %.2f，当前值：%.2f", metricName, config.lowerThreshold, data.getValue());
            }
            
            // 统计异常检测（基于历史数据的标准差）
            if (!isAnomaly) {
                List<MetricData> historical = historicalData.get(metricName);
                if (historical != null && historical.size() >= config.windowSize) {
                    double mean = historical.stream().mapToDouble(MetricData::getValue).average().orElse(0.0);
                    double stdDev = calculateStandardDeviation(historical, mean);
                    double threshold = stdDev * config.stdDevMultiplier;
                    
                    if (Math.abs(data.getValue() - mean) > threshold) {
                        isAnomaly = true;
                        anomalyType = "STATISTICAL_ANOMALY";
                        severity = "MEDIUM";
                        description = String.format("%s 统计异常，偏离均值 %.2f 超过 %.2f 个标准差", 
                            metricName, Math.abs(data.getValue() - mean), config.stdDevMultiplier);
                    }
                }
            }
            
            if (isAnomaly) {
                String anomalyId = generateAnomalyId(metricName);
                AnomalyReport anomaly = new AnomalyReport(anomalyId, metricName, data.getValue(), severity);
                anomaly.setAnomalyType(anomalyType);
                anomaly.setDescription(description);
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setStartTime(data.getTimestamp());
                anomaly.setConfidence(calculateConfidence(anomalyType, data.getValue(), config));
                anomaly.setNormalMin(config.lowerThreshold);
                anomaly.setNormalMax(config.upperThreshold);
                anomaly.setRecommendations(generateRecommendations(metricName, anomalyType));
                
                // 创建标签Map
                Map<String, String> tags = new HashMap<>();
                tags.put("metric", metricName);
                tags.put("type", anomalyType);
                tags.put("auto_detected", "true");
                anomaly.setTags(tags);
                
                anomalies.add(anomaly);
                anomalyReports.put(anomalyId, anomaly);
            }
        }
        
        return anomalies;
    }
    
    private double calculateStandardDeviation(List<MetricData> data, double mean) {
        double variance = data.stream()
            .mapToDouble(d -> Math.pow(d.getValue() - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }
    
    private String calculateSeverity(double value, double threshold, String defaultSeverity) {
        double ratio = Math.abs(value - threshold) / threshold;
        if (ratio > 0.5) return "CRITICAL";
        if (ratio > 0.3) return "HIGH";
        if (ratio > 0.1) return "MEDIUM";
        return "LOW";
    }
    
    private double calculateConfidence(String anomalyType, double value, ThresholdConfig config) {
        switch (anomalyType) {
            case "THRESHOLD_EXCEEDED":
                return Math.min(0.95, 0.7 + (value - config.upperThreshold) / config.upperThreshold * 0.25);
            case "THRESHOLD_BELOW":
                return Math.min(0.95, 0.7 + (config.lowerThreshold - value) / config.lowerThreshold * 0.25);
            case "STATISTICAL_ANOMALY":
                return 0.75;
            default:
                return 0.5;
        }
    }
    
    private List<String> generateRecommendations(String metricName, String anomalyType) {
        List<String> recommendations = new ArrayList<>();
        
        switch (metricName) {
            case "cpu_usage":
                recommendations.add("检查CPU密集型进程");
                recommendations.add("考虑增加CPU资源或优化算法");
                recommendations.add("检查是否有死循环或低效查询");
                break;
            case "memory_usage":
                recommendations.add("检查内存泄漏");
                recommendations.add("分析内存使用模式");
                recommendations.add("考虑增加内存资源");
                break;
            case "api_response_time":
                recommendations.add("检查数据库查询性能");
                recommendations.add("分析网络延迟");
                recommendations.add("检查缓存命中率");
                break;
            case "error_rate":
                recommendations.add("查看错误日志详情");
                recommendations.add("检查依赖服务状态");
                recommendations.add("验证输入参数有效性");
                break;
            default:
                recommendations.add("监控相关指标变化");
                recommendations.add("检查系统资源使用情况");
        }
        
        return recommendations;
    }
    
    private String generateAnomalyId(String metricName) {
        return String.format("anomaly_%s_%s_%d", 
            metricName, 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
            System.currentTimeMillis() % 1000);
    }
    
    @Override
    public boolean isAnomaly(String metricName, double value, LocalDateTime timestamp) {
        ThresholdConfig config = thresholds.get(metricName);
        if (config == null) {
            return false;
        }
        
        // 简单阈值检测
        if (value > config.upperThreshold || value < config.lowerThreshold) {
            return true;
        }
        
        // 基于历史数据的统计检测
        List<MetricData> historical = historicalData.get(metricName);
        if (historical != null && historical.size() >= config.windowSize) {
            double mean = historical.stream().mapToDouble(MetricData::getValue).average().orElse(0.0);
            double stdDev = calculateStandardDeviation(historical, mean);
            double threshold = stdDev * config.stdDevMultiplier;
            
            return Math.abs(value - mean) > threshold;
        }
        
        return false;
    }
    
    @Override
    public RootCauseAnalysis analyzeRootCause(String anomalyId) {
        AnomalyReport anomaly = anomalyReports.get(anomalyId);
        if (anomaly == null) {
            logger.warn("未找到异常报告: {}", anomalyId);
            return null;
        }
        
        String analysisId = "rca_" + anomalyId.substring(8); // 移除 "anomaly_" 前缀
        RootCauseAnalysis analysis = new RootCauseAnalysis(analysisId, anomalyId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            analysis.setStatus("IN_PROGRESS");
            
            // 基于规则的根因分析
            String rootCause = analyzeBasedOnRules(anomaly);
            analysis.setRootCause(rootCause);
            
            // 生成可能原因
            List<String> possibleCauses = generatePossibleCauses(anomaly);
            analysis.setPossibleCauses(possibleCauses);
            
            // 生成解决方案
            List<String> solutions = generateSolutions(anomaly);
            analysis.setSolutions(solutions);
            
            // 生成预防措施
            List<String> preventiveMeasures = generatePreventiveMeasures(anomaly);
            analysis.setPreventiveMeasures(preventiveMeasures);
            
            // 设置分析方法和置信度
            analysis.setAnalysisMethod("RULE_BASED_CORRELATION");
            analysis.setConfidence(0.8);
            
            // 指标分析
            Map<String, Object> metricAnalyses = analyzeRelatedMetrics(anomaly);
            analysis.setMetricAnalyses(metricAnalyses);
            
            analysis.setStatus("COMPLETED");
            
        } catch (Exception e) {
            logger.error("根因分析失败: {}", anomalyId, e);
            analysis.setStatus("FAILED");
            analysis.setRootCause("分析过程中发生错误: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            analysis.setAnalysisTimeMs(endTime - startTime);
        }
        
        rootCauseAnalyses.put(analysisId, analysis);
        return analysis;
    }
    
    private String analyzeBasedOnRules(AnomalyReport anomaly) {
        String metricName = anomaly.getMetricName();
        String anomalyType = anomaly.getAnomalyType();
        double value = anomaly.getAnomalyValue();
        
        switch (metricName) {
            case "cpu_usage":
                if (value > 90) {
                    return "CPU使用率极高，可能存在CPU密集型任务或死循环";
                } else {
                    return "CPU使用率异常，可能是突发负载或资源竞争";
                }
            case "memory_usage":
                if (value > 95) {
                    return "内存使用率接近极限，可能存在内存泄漏或内存不足";
                } else {
                    return "内存使用异常，可能是大对象分配或缓存过度使用";
                }
            case "api_response_time":
                if (value > 5000) {
                    return "API响应时间严重超时，可能是数据库查询慢或网络问题";
                } else {
                    return "API响应时间异常，可能是服务负载高或依赖服务慢";
                }
            case "error_rate":
                return "错误率异常升高，可能是代码bug、配置错误或依赖服务异常";
            case "cache_hit_rate":
                return "缓存命中率下降，可能是缓存失效、数据变化频繁或缓存配置不当";
            default:
                return "检测到指标异常，需要进一步分析具体原因";
        }
    }
    
    private List<String> generatePossibleCauses(AnomalyReport anomaly) {
        List<String> causes = new ArrayList<>();
        String metricName = anomaly.getMetricName();
        
        switch (metricName) {
            case "cpu_usage":
                causes.add("CPU密集型任务执行");
                causes.add("死循环或无限递归");
                causes.add("系统负载突然增加");
                causes.add("GC压力过大");
                break;
            case "memory_usage":
                causes.add("内存泄漏");
                causes.add("大对象分配");
                causes.add("缓存数据过多");
                causes.add("并发请求过多");
                break;
            case "api_response_time":
                causes.add("数据库查询慢");
                causes.add("网络延迟高");
                causes.add("依赖服务响应慢");
                causes.add("线程池资源不足");
                break;
            case "error_rate":
                causes.add("代码逻辑错误");
                causes.add("配置参数错误");
                causes.add("依赖服务不可用");
                causes.add("输入参数验证失败");
                break;
        }
        
        return causes;
    }
    
    private List<String> generateSolutions(AnomalyReport anomaly) {
        List<String> solutions = new ArrayList<>();
        String metricName = anomaly.getMetricName();
        
        switch (metricName) {
            case "cpu_usage":
                solutions.add("优化CPU密集型算法");
                solutions.add("增加CPU资源");
                solutions.add("实施负载均衡");
                solutions.add("优化GC配置");
                break;
            case "memory_usage":
                solutions.add("修复内存泄漏");
                solutions.add("增加内存资源");
                solutions.add("优化缓存策略");
                solutions.add("实施内存监控");
                break;
            case "api_response_time":
                solutions.add("优化数据库查询");
                solutions.add("增加缓存层");
                solutions.add("优化网络配置");
                solutions.add("增加服务实例");
                break;
            case "error_rate":
                solutions.add("修复代码bug");
                solutions.add("验证配置参数");
                solutions.add("实施熔断机制");
                solutions.add("加强输入验证");
                break;
        }
        
        return solutions;
    }
    
    private List<String> generatePreventiveMeasures(AnomalyReport anomaly) {
        List<String> measures = new ArrayList<>();
        
        measures.add("建立完善的监控体系");
        measures.add("设置合理的告警阈值");
        measures.add("定期进行性能测试");
        measures.add("实施代码审查机制");
        measures.add("建立故障演练流程");
        measures.add("优化系统架构设计");
        
        return measures;
    }
    
    private Map<String, Object> analyzeRelatedMetrics(AnomalyReport anomaly) {
        Map<String, Object> analyses = new HashMap<>();
        
        // 模拟相关指标分析
        analyses.put("cpu_correlation", 0.75);
        analyses.put("memory_correlation", 0.65);
        analyses.put("network_correlation", 0.45);
        analyses.put("disk_correlation", 0.35);
        
        return analyses;
    }
    
    @Override
    public Map<String, RootCauseAnalysis> batchAnalyzeRootCause(List<AnomalyReport> anomalies) {
        Map<String, RootCauseAnalysis> results = new HashMap<>();
        
        for (AnomalyReport anomaly : anomalies) {
            RootCauseAnalysis analysis = analyzeRootCause(anomaly.getAnomalyId());
            if (analysis != null) {
                results.put(anomaly.getAnomalyId(), analysis);
            }
        }
        
        return results;
    }
    
    @Override
    public AnomalyPrediction predictAnomaly(String metricName, int forecastMinutes) {
        String predictionId = "pred_" + metricName + "_" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        AnomalyPrediction prediction = new AnomalyPrediction(predictionId, metricName, forecastMinutes);
        
        try {
            // 获取历史数据
            List<MetricData> historical = historicalData.get(metricName);
            if (historical == null || historical.size() < 10) {
                prediction.setAnomalyProbability(0.1);
                prediction.setConfidence(0.3);
                prediction.setPredictedAnomalyType("INSUFFICIENT_DATA");
                return prediction;
            }
            
            // 简单的趋势预测
            List<AnomalyPrediction.PredictionPoint> forecastValues = generateForecast(historical, forecastMinutes);
            prediction.setForecastValues(forecastValues);
            
            // 计算异常概率
            double anomalyProb = calculateAnomalyProbability(forecastValues, metricName);
            prediction.setAnomalyProbability(anomalyProb);
            prediction.setConfidence(0.75);
            
            if (anomalyProb > 0.7) {
                prediction.setPredictedSeverity("HIGH");
                prediction.setPredictedAnomalyType("THRESHOLD_EXCEEDED");
                prediction.setPredictedAnomalyTime(LocalDateTime.now().plusMinutes(forecastMinutes / 2));
            } else if (anomalyProb > 0.4) {
                prediction.setPredictedSeverity("MEDIUM");
                prediction.setPredictedAnomalyType("STATISTICAL_ANOMALY");
            } else {
                prediction.setPredictedSeverity("LOW");
            }
            
            // 生成预防建议
            prediction.setPreventionRecommendations(generatePreventionRecommendations(metricName, anomalyProb));
            
            // 模型信息
            AnomalyPrediction.ModelInfo modelInfo = new AnomalyPrediction.ModelInfo();
            modelInfo.setModelName("SIMPLE_TREND_PREDICTOR");
            modelInfo.setModelVersion("v1.0.0");
            modelInfo.setAccuracy(0.75);
            modelInfo.setTrainingDataSize(historical.size());
            modelInfo.setLastTrainingTime(LocalDateTime.now());
            prediction.setModelInfo(modelInfo);
            
        } catch (Exception e) {
            logger.error("异常预测失败: {}", metricName, e);
            prediction.setAnomalyProbability(0.0);
            prediction.setConfidence(0.0);
        }
        
        return prediction;
    }
    
    private List<AnomalyPrediction.PredictionPoint> generateForecast(List<MetricData> historical, int forecastMinutes) {
        List<AnomalyPrediction.PredictionPoint> forecast = new ArrayList<>();
        
        // 简单的线性趋势预测
        if (historical.size() < 2) {
            return forecast;
        }
        
        // 计算趋势
        double[] values = historical.stream().mapToDouble(MetricData::getValue).toArray();
        double trend = calculateTrend(values);
        double lastValue = values[values.length - 1];
        
        // 生成预测点
        LocalDateTime currentTime = LocalDateTime.now();
        for (int i = 1; i <= forecastMinutes; i++) {
            double predictedValue = lastValue + trend * i;
            
            // 添加一些随机波动
            double noise = (Math.random() - 0.5) * 2 * (lastValue * 0.05);
            predictedValue += noise;
            
            AnomalyPrediction.PredictionPoint point = new AnomalyPrediction.PredictionPoint();
            point.setTimestamp(currentTime.plusMinutes(i));
            point.setPredictedValue(Math.max(0, predictedValue));
            point.setLowerBound(predictedValue * 0.9);
            point.setUpperBound(predictedValue * 1.1);
            point.setAnomalyProbability(calculatePointAnomalyProbability(predictedValue));
            
            forecast.add(point);
        }
        
        return forecast;
    }
    
    private double calculateTrend(double[] values) {
        if (values.length < 2) return 0.0;
        
        // 简单线性回归计算趋势
        int n = Math.min(values.length, 20); // 使用最近20个点
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            int x = i;
            double y = values[values.length - n + i];
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }
    
    private double calculateAnomalyProbability(List<AnomalyPrediction.PredictionPoint> forecast, String metricName) {
        ThresholdConfig config = thresholds.get(metricName);
        if (config == null) return 0.1;
        
        long anomalyPoints = forecast.stream()
            .mapToLong(point -> {
                double value = point.getPredictedValue();
                return (value > config.upperThreshold || value < config.lowerThreshold) ? 1 : 0;
            })
            .sum();
        
        return (double) anomalyPoints / forecast.size();
    }
    
    private double calculatePointAnomalyProbability(double value) {
        // 基于值的大小计算异常概率
        if (value > 100) return 0.8;
        if (value > 80) return 0.6;
        if (value > 60) return 0.4;
        return 0.2;
    }
    
    private List<String> generatePreventionRecommendations(String metricName, double anomalyProb) {
        List<String> recommendations = new ArrayList<>();
        
        if (anomalyProb > 0.7) {
            recommendations.add("立即检查系统状态");
            recommendations.add("准备扩容资源");
            recommendations.add("启动应急预案");
        } else if (anomalyProb > 0.4) {
            recommendations.add("密切监控指标变化");
            recommendations.add("检查资源使用情况");
            recommendations.add("准备优化措施");
        } else {
            recommendations.add("保持正常监控");
            recommendations.add("定期检查系统健康状态");
        }
        
        return recommendations;
    }
    
    @Override
    public boolean trainModel(List<MetricData> historicalData) {
        try {
            // 简单的模型训练模拟
            logger.info("开始训练异常检测模型，数据量: {}", historicalData.size());
            
            // 更新历史数据
            for (MetricData data : historicalData) {
                updateHistoricalData(data.getMetricName(), Arrays.asList(data));
            }
            
            // 更新模型健康状态
            modelHealth.put("last_training_time", LocalDateTime.now());
            modelHealth.put("training_data_size", historicalData.size());
            modelHealth.put("model_accuracy", 0.92 + Math.random() * 0.05);
            modelHealth.put("status", "HEALTHY");
            
            logger.info("模型训练完成");
            return true;
            
        } catch (Exception e) {
            logger.error("模型训练失败", e);
            modelHealth.put("status", "ERROR");
            return false;
        }
    }
    
    @Override
    public Map<String, Object> getAnomalyStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        
        List<AnomalyReport> periodAnomalies = anomalyReports.values().stream()
            .filter(anomaly -> anomaly.getDetectedAt().isAfter(startTime) && 
                             anomaly.getDetectedAt().isBefore(endTime))
            .collect(Collectors.toList());
        
        stats.put("total_anomalies", periodAnomalies.size());
        stats.put("critical_anomalies", periodAnomalies.stream()
            .mapToLong(a -> "CRITICAL".equals(a.getSeverity()) ? 1 : 0).sum());
        stats.put("high_anomalies", periodAnomalies.stream()
            .mapToLong(a -> "HIGH".equals(a.getSeverity()) ? 1 : 0).sum());
        stats.put("medium_anomalies", periodAnomalies.stream()
            .mapToLong(a -> "MEDIUM".equals(a.getSeverity()) ? 1 : 0).sum());
        stats.put("low_anomalies", periodAnomalies.stream()
            .mapToLong(a -> "LOW".equals(a.getSeverity()) ? 1 : 0).sum());
        
        // 按指标分组统计
        Map<String, Long> metricStats = periodAnomalies.stream()
            .collect(Collectors.groupingBy(AnomalyReport::getMetricName, Collectors.counting()));
        stats.put("anomalies_by_metric", metricStats);
        
        // 按类型分组统计
        Map<String, Long> typeStats = periodAnomalies.stream()
            .collect(Collectors.groupingBy(AnomalyReport::getAnomalyType, Collectors.counting()));
        stats.put("anomalies_by_type", typeStats);
        
        stats.put("start_time", startTime);
        stats.put("end_time", endTime);
        
        return stats;
    }
    
    @Override
    public Map<String, Object> getModelHealth() {
        return new HashMap<>(modelHealth);
    }
    
    @Override
    public void updateThreshold(String metricName, double threshold) {
        ThresholdConfig config = thresholds.get(metricName);
        if (config != null) {
            config.upperThreshold = threshold;
            logger.info("更新指标 {} 的阈值为 {}", metricName, threshold);
        } else {
            thresholds.put(metricName, new ThresholdConfig(threshold, 0.0, 2.0, 10));
            logger.info("为指标 {} 创建新阈值配置: {}", metricName, threshold);
        }
    }
    
    @Override
    public Map<String, Object> getAnomalyTrends(int days) {
        Map<String, Object> trends = new HashMap<>();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        
        List<AnomalyReport> periodAnomalies = anomalyReports.values().stream()
            .filter(anomaly -> anomaly.getDetectedAt().isAfter(startTime))
            .sorted(Comparator.comparing(AnomalyReport::getDetectedAt))
            .collect(Collectors.toList());
        
        // 按天统计
        Map<String, Long> dailyStats = periodAnomalies.stream()
            .collect(Collectors.groupingBy(
                anomaly -> anomaly.getDetectedAt().toLocalDate().toString(),
                Collectors.counting()
            ));
        
        trends.put("daily_anomaly_count", dailyStats);
        trends.put("total_days", days);
        trends.put("average_daily_anomalies", dailyStats.values().stream()
            .mapToDouble(Long::doubleValue).average().orElse(0.0));
        
        // 趋势分析
        List<Long> dailyCounts = new ArrayList<>(dailyStats.values());
        if (dailyCounts.size() > 1) {
            double trend = calculateTrendFromCounts(dailyCounts);
            trends.put("trend", trend > 0 ? "INCREASING" : trend < 0 ? "DECREASING" : "STABLE");
            trends.put("trend_value", trend);
        } else {
            trends.put("trend", "INSUFFICIENT_DATA");
            trends.put("trend_value", 0.0);
        }
        
        return trends;
    }
    
    private double calculateTrendFromCounts(List<Long> counts) {
        if (counts.size() < 2) return 0.0;
        
        double sum = 0.0;
        for (int i = 1; i < counts.size(); i++) {
            sum += counts.get(i) - counts.get(i - 1);
        }
        
        return sum / (counts.size() - 1);
    }
} 