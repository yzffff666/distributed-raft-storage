package com.github.raftimpl.raft.api.service;

import java.util.concurrent.CompletableFuture;

/**
 * 网络优化服务接口
 * 提供网络通信和序列化性能优化功能
 * 
 */
public interface NetworkOptimizationService {

    /**
     * 压缩数据
     * 
     * @param data 原始数据
     * @return 压缩后的数据
     */
    byte[] compressData(byte[] data);

    /**
     * 解压缩数据
     * 
     * @param compressedData 压缩数据
     * @return 解压缩后的数据
     */
    byte[] decompressData(byte[] compressedData);

    /**
     * 序列化对象
     * 
     * @param object 待序列化的对象
     * @return 序列化后的字节数组
     */
    byte[] serialize(Object object);

    /**
     * 反序列化对象
     * 
     * @param data 序列化数据
     * @param clazz 目标类型
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] data, Class<T> clazz);

    /**
     * 异步发送数据
     * 
     * @param targetNode 目标节点
     * @param data 数据
     * @return 发送结果的Future
     */
    CompletableFuture<Boolean> sendDataAsync(String targetNode, byte[] data);

    /**
     * 批量发送数据
     * 
     * @param targetNode 目标节点
     * @param dataList 数据列表
     * @return 发送结果的Future
     */
    CompletableFuture<Boolean> batchSendData(String targetNode, byte[][] dataList);

    /**
     * 启用连接池
     * 
     * @param poolSize 连接池大小
     */
    void enableConnectionPool(int poolSize);

    /**
     * 启用请求管道化
     * 
     * @param enabled 是否启用
     */
    void enablePipelining(boolean enabled);

    /**
     * 设置网络超时时间
     * 
     * @param connectTimeoutMs 连接超时时间（毫秒）
     * @param readTimeoutMs 读取超时时间（毫秒）
     */
    void setNetworkTimeout(int connectTimeoutMs, int readTimeoutMs);

    /**
     * 获取网络统计信息
     * 
     * @return 网络统计信息
     */
    NetworkStats getNetworkStats();

    /**
     * 网络统计信息类
     */
    class NetworkStats {
        private long totalBytesSent;
        private long totalBytesReceived;
        private long totalRequestsSent;
        private long totalResponsesReceived;
        private double averageLatency;
        private double compressionRatio;
        private int activeConnections;
        private long errorCount;

        // 构造函数
        public NetworkStats() {}

        // Getters and Setters
        public long getTotalBytesSent() { return totalBytesSent; }
        public void setTotalBytesSent(long totalBytesSent) { this.totalBytesSent = totalBytesSent; }

        public long getTotalBytesReceived() { return totalBytesReceived; }
        public void setTotalBytesReceived(long totalBytesReceived) { this.totalBytesReceived = totalBytesReceived; }

        public long getTotalRequestsSent() { return totalRequestsSent; }
        public void setTotalRequestsSent(long totalRequestsSent) { this.totalRequestsSent = totalRequestsSent; }

        public long getTotalResponsesReceived() { return totalResponsesReceived; }
        public void setTotalResponsesReceived(long totalResponsesReceived) { this.totalResponsesReceived = totalResponsesReceived; }

        public double getAverageLatency() { return averageLatency; }
        public void setAverageLatency(double averageLatency) { this.averageLatency = averageLatency; }

        public double getCompressionRatio() { return compressionRatio; }
        public void setCompressionRatio(double compressionRatio) { this.compressionRatio = compressionRatio; }

        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }

        public long getErrorCount() { return errorCount; }
        public void setErrorCount(long errorCount) { this.errorCount = errorCount; }

        /**
         * 计算网络吞吐量（字节/秒）
         * 
         * @param durationSeconds 统计持续时间（秒）
         * @return 吞吐量
         */
        public double getThroughput(long durationSeconds) {
            if (durationSeconds <= 0) return 0.0;
            return (double) (totalBytesSent + totalBytesReceived) / durationSeconds;
        }

        /**
         * 计算错误率
         * 
         * @return 错误率（0-1之间）
         */
        public double getErrorRate() {
            long totalRequests = totalRequestsSent;
            if (totalRequests <= 0) return 0.0;
            return (double) errorCount / totalRequests;
        }
    }
} 