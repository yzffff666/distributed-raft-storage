package com.github.raftimpl.raft.api.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 异步处理服务接口
 * 提供异步处理和批量操作功能，提升系统性能
 * 
 */
public interface AsyncProcessingService {

    /**
     * 异步处理单个任务
     * 
     * @param task 任务
     * @return 处理结果的Future
     */
    <T, R> CompletableFuture<R> processAsync(Task<T, R> task);

    /**
     * 批量异步处理任务
     * 
     * @param tasks 任务列表
     * @return 处理结果列表的Future
     */
    <T, R> CompletableFuture<List<R>> batchProcessAsync(List<Task<T, R>> tasks);

    /**
     * 异步写入数据
     * 
     * @param key 键
     * @param value 值
     * @return 写入结果的Future
     */
    CompletableFuture<Boolean> writeAsync(String key, byte[] value);

    /**
     * 批量异步写入数据
     * 
     * @param data 数据映射
     * @return 写入结果的Future
     */
    CompletableFuture<BatchResult> batchWriteAsync(java.util.Map<String, byte[]> data);

    /**
     * 异步读取数据
     * 
     * @param key 键
     * @return 读取结果的Future
     */
    CompletableFuture<byte[]> readAsync(String key);

    /**
     * 批量异步读取数据
     * 
     * @param keys 键列表
     * @return 读取结果映射的Future
     */
    CompletableFuture<java.util.Map<String, byte[]>> batchReadAsync(List<String> keys);

    /**
     * 异步删除数据
     * 
     * @param key 键
     * @return 删除结果的Future
     */
    CompletableFuture<Boolean> deleteAsync(String key);

    /**
     * 批量异步删除数据
     * 
     * @param keys 键列表
     * @return 删除结果的Future
     */
    CompletableFuture<BatchResult> batchDeleteAsync(List<String> keys);

    /**
     * 设置批处理参数
     * 
     * @param batchSize 批处理大小
     * @param batchDelayMs 批处理延迟（毫秒）
     */
    void setBatchConfig(int batchSize, int batchDelayMs);

    /**
     * 获取异步处理统计信息
     * 
     * @return 统计信息
     */
    AsyncStats getAsyncStats();

    /**
     * 任务接口
     */
    @FunctionalInterface
    interface Task<T, R> {
        R execute(T input) throws Exception;
    }

    /**
     * 批量操作结果
     */
    class BatchResult {
        private final int totalCount;
        private final int successCount;
        private final int failureCount;
        private final List<String> errors;

        public BatchResult(int totalCount, int successCount, int failureCount, List<String> errors) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.errors = errors;
        }

        // Getters
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<String> getErrors() { return errors; }
        public double getSuccessRate() { 
            return totalCount > 0 ? (double) successCount / totalCount : 0.0; 
        }
    }

    /**
     * 异步处理统计信息
     */
    class AsyncStats {
        private long totalAsyncTasks;
        private long completedAsyncTasks;
        private long failedAsyncTasks;
        private long totalBatchOperations;
        private long completedBatchOperations;
        private double averageTaskDuration;
        private double averageBatchSize;
        private int activeThreads;
        private int queuedTasks;

        // 构造函数
        public AsyncStats() {}

        // Getters and Setters
        public long getTotalAsyncTasks() { return totalAsyncTasks; }
        public void setTotalAsyncTasks(long totalAsyncTasks) { this.totalAsyncTasks = totalAsyncTasks; }

        public long getCompletedAsyncTasks() { return completedAsyncTasks; }
        public void setCompletedAsyncTasks(long completedAsyncTasks) { this.completedAsyncTasks = completedAsyncTasks; }

        public long getFailedAsyncTasks() { return failedAsyncTasks; }
        public void setFailedAsyncTasks(long failedAsyncTasks) { this.failedAsyncTasks = failedAsyncTasks; }

        public long getTotalBatchOperations() { return totalBatchOperations; }
        public void setTotalBatchOperations(long totalBatchOperations) { this.totalBatchOperations = totalBatchOperations; }

        public long getCompletedBatchOperations() { return completedBatchOperations; }
        public void setCompletedBatchOperations(long completedBatchOperations) { this.completedBatchOperations = completedBatchOperations; }

        public double getAverageTaskDuration() { return averageTaskDuration; }
        public void setAverageTaskDuration(double averageTaskDuration) { this.averageTaskDuration = averageTaskDuration; }

        public double getAverageBatchSize() { return averageBatchSize; }
        public void setAverageBatchSize(double averageBatchSize) { this.averageBatchSize = averageBatchSize; }

        public int getActiveThreads() { return activeThreads; }
        public void setActiveThreads(int activeThreads) { this.activeThreads = activeThreads; }

        public int getQueuedTasks() { return queuedTasks; }
        public void setQueuedTasks(int queuedTasks) { this.queuedTasks = queuedTasks; }

        /**
         * 计算任务成功率
         * 
         * @return 成功率（0-1之间）
         */
        public double getTaskSuccessRate() {
            if (totalAsyncTasks <= 0) return 0.0;
            return (double) completedAsyncTasks / totalAsyncTasks;
        }

        /**
         * 计算批处理效率
         * 
         * @return 批处理效率
         */
        public double getBatchEfficiency() {
            if (totalBatchOperations <= 0) return 0.0;
            return (double) completedBatchOperations / totalBatchOperations;
        }
    }
} 