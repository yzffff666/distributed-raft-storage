package com.github.raftimpl.raft.api.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 性能测试服务接口
 * 提供性能基准测试和压力测试功能
 * 
 */
public interface PerformanceTestService {

    /**
     * 执行写入性能测试
     * 
     * @param config 测试配置
     * @return 测试结果
     */
    CompletableFuture<TestResult> runWritePerformanceTest(TestConfig config);

    /**
     * 执行读取性能测试
     * 
     * @param config 测试配置
     * @return 测试结果
     */
    CompletableFuture<TestResult> runReadPerformanceTest(TestConfig config);

    /**
     * 执行混合读写性能测试
     * 
     * @param config 测试配置
     * @return 测试结果
     */
    CompletableFuture<TestResult> runMixedPerformanceTest(TestConfig config);

    /**
     * 执行并发压力测试
     * 
     * @param config 测试配置
     * @return 测试结果
     */
    CompletableFuture<TestResult> runConcurrencyStressTest(TestConfig config);

    /**
     * 执行内存压力测试
     * 
     * @param config 测试配置
     * @return 测试结果
     */
    CompletableFuture<TestResult> runMemoryStressTest(TestConfig config);

    /**
     * 执行网络延迟测试
     * 
     * @param config 测试配置
     * @return 测试结果
     */
    CompletableFuture<TestResult> runNetworkLatencyTest(TestConfig config);

    /**
     * 获取系统基准性能指标
     * 
     * @return 基准指标
     */
    BenchmarkMetrics getBenchmarkMetrics();

    /**
     * 生成性能测试报告
     * 
     * @param results 测试结果列表
     * @return 性能报告
     */
    PerformanceReport generateReport(List<TestResult> results);

    /**
     * 测试配置类
     */
    class TestConfig {
        private int threadCount = 10;
        private int operationCount = 1000;
        private int durationSeconds = 60;
        private int dataSize = 1024; // bytes
        private double readRatio = 0.7; // 读写比例
        private boolean enableBatching = false;
        private int batchSize = 100;
        private String testName;

        // 构造函数
        public TestConfig() {}

        public TestConfig(String testName) {
            this.testName = testName;
        }

        // Getters and Setters
        public int getThreadCount() { return threadCount; }
        public void setThreadCount(int threadCount) { this.threadCount = threadCount; }

        public int getOperationCount() { return operationCount; }
        public void setOperationCount(int operationCount) { this.operationCount = operationCount; }

        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

        public int getDataSize() { return dataSize; }
        public void setDataSize(int dataSize) { this.dataSize = dataSize; }

        public double getReadRatio() { return readRatio; }
        public void setReadRatio(double readRatio) { this.readRatio = readRatio; }

        public boolean isEnableBatching() { return enableBatching; }
        public void setEnableBatching(boolean enableBatching) { this.enableBatching = enableBatching; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
    }

    /**
     * 测试结果类
     */
    class TestResult {
        private String testName;
        private long startTime;
        private long endTime;
        private long totalOperations;
        private long successfulOperations;
        private long failedOperations;
        private double throughput; // ops/sec
        private double averageLatency; // ms
        private double p50Latency;
        private double p95Latency;
        private double p99Latency;
        private double maxLatency;
        private double minLatency;
        private long totalDataTransferred; // bytes
        private double cpuUsage;
        private double memoryUsage;
        private Map<String, Object> additionalMetrics;

        // 构造函数
        public TestResult() {}

        public TestResult(String testName) {
            this.testName = testName;
            this.startTime = System.currentTimeMillis();
        }

        // Getters and Setters
        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }

        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }

        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }

        public long getTotalOperations() { return totalOperations; }
        public void setTotalOperations(long totalOperations) { this.totalOperations = totalOperations; }

        public long getSuccessfulOperations() { return successfulOperations; }
        public void setSuccessfulOperations(long successfulOperations) { this.successfulOperations = successfulOperations; }

        public long getFailedOperations() { return failedOperations; }
        public void setFailedOperations(long failedOperations) { this.failedOperations = failedOperations; }

        public double getThroughput() { return throughput; }
        public void setThroughput(double throughput) { this.throughput = throughput; }

        public double getAverageLatency() { return averageLatency; }
        public void setAverageLatency(double averageLatency) { this.averageLatency = averageLatency; }

        public double getP50Latency() { return p50Latency; }
        public void setP50Latency(double p50Latency) { this.p50Latency = p50Latency; }

        public double getP95Latency() { return p95Latency; }
        public void setP95Latency(double p95Latency) { this.p95Latency = p95Latency; }

        public double getP99Latency() { return p99Latency; }
        public void setP99Latency(double p99Latency) { this.p99Latency = p99Latency; }

        public double getMaxLatency() { return maxLatency; }
        public void setMaxLatency(double maxLatency) { this.maxLatency = maxLatency; }

        public double getMinLatency() { return minLatency; }
        public void setMinLatency(double minLatency) { this.minLatency = minLatency; }

        public long getTotalDataTransferred() { return totalDataTransferred; }
        public void setTotalDataTransferred(long totalDataTransferred) { this.totalDataTransferred = totalDataTransferred; }

        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

        public double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }

        public Map<String, Object> getAdditionalMetrics() { return additionalMetrics; }
        public void setAdditionalMetrics(Map<String, Object> additionalMetrics) { this.additionalMetrics = additionalMetrics; }

        /**
         * 计算测试持续时间
         * 
         * @return 持续时间（毫秒）
         */
        public long getDuration() {
            return endTime - startTime;
        }

        /**
         * 计算成功率
         * 
         * @return 成功率（0-1之间）
         */
        public double getSuccessRate() {
            if (totalOperations <= 0) return 0.0;
            return (double) successfulOperations / totalOperations;
        }

        /**
         * 计算数据吞吐量（MB/s）
         * 
         * @return 数据吞吐量
         */
        public double getDataThroughput() {
            long durationMs = getDuration();
            if (durationMs <= 0) return 0.0;
            return (double) totalDataTransferred / (1024 * 1024) / (durationMs / 1000.0);
        }
    }

    /**
     * 基准指标类
     */
    class BenchmarkMetrics {
        private double maxWriteThroughput;
        private double maxReadThroughput;
        private double minWriteLatency;
        private double minReadLatency;
        private int maxConcurrentConnections;
        private long maxMemoryUsage;
        private double systemCpuCores;
        private long systemMemorySize;

        // 构造函数
        public BenchmarkMetrics() {}

        // Getters and Setters
        public double getMaxWriteThroughput() { return maxWriteThroughput; }
        public void setMaxWriteThroughput(double maxWriteThroughput) { this.maxWriteThroughput = maxWriteThroughput; }

        public double getMaxReadThroughput() { return maxReadThroughput; }
        public void setMaxReadThroughput(double maxReadThroughput) { this.maxReadThroughput = maxReadThroughput; }

        public double getMinWriteLatency() { return minWriteLatency; }
        public void setMinWriteLatency(double minWriteLatency) { this.minWriteLatency = minWriteLatency; }

        public double getMinReadLatency() { return minReadLatency; }
        public void setMinReadLatency(double minReadLatency) { this.minReadLatency = minReadLatency; }

        public int getMaxConcurrentConnections() { return maxConcurrentConnections; }
        public void setMaxConcurrentConnections(int maxConcurrentConnections) { this.maxConcurrentConnections = maxConcurrentConnections; }

        public long getMaxMemoryUsage() { return maxMemoryUsage; }
        public void setMaxMemoryUsage(long maxMemoryUsage) { this.maxMemoryUsage = maxMemoryUsage; }

        public double getSystemCpuCores() { return systemCpuCores; }
        public void setSystemCpuCores(double systemCpuCores) { this.systemCpuCores = systemCpuCores; }

        public long getSystemMemorySize() { return systemMemorySize; }
        public void setSystemMemorySize(long systemMemorySize) { this.systemMemorySize = systemMemorySize; }
    }

    /**
     * 性能报告类
     */
    class PerformanceReport {
        private String reportId;
        private long generateTime;
        private List<TestResult> testResults;
        private BenchmarkMetrics benchmarkMetrics;
        private Map<String, Object> summary;
        private List<String> recommendations;

        // 构造函数
        public PerformanceReport() {
            this.reportId = "PERF_" + System.currentTimeMillis();
            this.generateTime = System.currentTimeMillis();
        }

        // Getters and Setters
        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }

        public long getGenerateTime() { return generateTime; }
        public void setGenerateTime(long generateTime) { this.generateTime = generateTime; }

        public List<TestResult> getTestResults() { return testResults; }
        public void setTestResults(List<TestResult> testResults) { this.testResults = testResults; }

        public BenchmarkMetrics getBenchmarkMetrics() { return benchmarkMetrics; }
        public void setBenchmarkMetrics(BenchmarkMetrics benchmarkMetrics) { this.benchmarkMetrics = benchmarkMetrics; }

        public Map<String, Object> getSummary() { return summary; }
        public void setSummary(Map<String, Object> summary) { this.summary = summary; }

        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }
} 