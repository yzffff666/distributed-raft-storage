package com.github.raftimpl.raft.api.service.impl;

import com.github.raftimpl.raft.api.service.NetworkOptimizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 网络优化服务实现
 * 提供高性能的网络通信和序列化功能
 * 
 */
@Service
public class NetworkOptimizationServiceImpl implements NetworkOptimizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkOptimizationServiceImpl.class);
    
    /** 网络统计信息 */
    private final NetworkStats networkStats = new NetworkStats();
    
    /** 连接池配置 */
    private int connectionPoolSize = 50;
    private boolean pipeliningEnabled = true;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    
    /** 异步执行器 */
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(20);
    
    /** 连接统计 */
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong totalRequestsSent = new AtomicLong(0);
    private final AtomicLong totalResponsesReceived = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    /** 延迟统计 */
    private final ConcurrentHashMap<String, Long> latencySum = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> latencyCount = new ConcurrentHashMap<>();

    @Override
    public byte[] compressData(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(data);
                gzipOut.finish();
            }
            
            byte[] compressed = baos.toByteArray();
            
            // 更新压缩统计
            double ratio = (double) compressed.length / data.length;
            networkStats.setCompressionRatio(ratio);
            
            logger.debug("数据压缩完成: 原始大小={}, 压缩后大小={}, 压缩比={}", 
                    data.length, compressed.length, ratio);
            
            return compressed;
        } catch (IOException e) {
            logger.error("数据压缩失败", e);
            errorCount.incrementAndGet();
            return data; // 压缩失败返回原始数据
        }
    }

    @Override
    public byte[] decompressData(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return compressedData;
        }
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            try (GZIPInputStream gzipIn = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = gzipIn.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                
                byte[] decompressed = baos.toByteArray();
                logger.debug("数据解压缩完成: 压缩大小={}, 解压后大小={}", 
                        compressedData.length, decompressed.length);
                
                return decompressed;
            }
        } catch (IOException e) {
            logger.error("数据解压缩失败", e);
            errorCount.incrementAndGet();
            return compressedData; // 解压失败返回原始数据
        }
    }

    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return new byte[0];
        }
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(object);
                oos.flush();
            }
            
            byte[] serialized = baos.toByteArray();
            logger.debug("对象序列化完成: 类型={}, 大小={}", 
                    object.getClass().getSimpleName(), serialized.length);
            
            return serialized;
        } catch (IOException e) {
            logger.error("对象序列化失败: {}", object.getClass().getSimpleName(), e);
            errorCount.incrementAndGet();
            throw new RuntimeException("序列化失败", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                Object obj = ois.readObject();
                
                if (clazz.isInstance(obj)) {
                    logger.debug("对象反序列化完成: 类型={}, 大小={}", 
                            clazz.getSimpleName(), data.length);
                    return clazz.cast(obj);
                } else {
                    throw new ClassCastException("反序列化类型不匹配: 期望=" + clazz.getSimpleName() + 
                            ", 实际=" + obj.getClass().getSimpleName());
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.error("对象反序列化失败: {}", clazz.getSimpleName(), e);
            errorCount.incrementAndGet();
            throw new RuntimeException("反序列化失败", e);
        }
    }

    @Override
    public CompletableFuture<Boolean> sendDataAsync(String targetNode, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // 模拟网络发送（实际实现中会使用真实的网络客户端）
                logger.debug("异步发送数据到节点: {}, 大小: {}", targetNode, data.length);
                
                // 模拟网络延迟
                Thread.sleep(10);
                
                // 更新统计信息
                totalBytesSent.addAndGet(data.length);
                totalRequestsSent.incrementAndGet();
                
                long latency = System.currentTimeMillis() - startTime;
                updateLatencyStats(targetNode, latency);
                
                logger.debug("数据发送完成: 节点={}, 延迟={}ms", targetNode, latency);
                return true;
                
            } catch (Exception e) {
                logger.error("异步发送数据失败: 节点={}", targetNode, e);
                errorCount.incrementAndGet();
                return false;
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> batchSendData(String targetNode, byte[][] dataList) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                logger.debug("批量发送数据到节点: {}, 批次大小: {}", targetNode, dataList.length);
                
                long totalBytes = 0;
                for (byte[] data : dataList) {
                    if (data != null) {
                        totalBytes += data.length;
                    }
                }
                
                // 模拟批量网络发送
                Thread.sleep(dataList.length * 2); // 批量发送减少延迟
                
                // 更新统计信息
                totalBytesSent.addAndGet(totalBytes);
                totalRequestsSent.addAndGet(dataList.length);
                
                long latency = System.currentTimeMillis() - startTime;
                updateLatencyStats(targetNode, latency);
                
                logger.debug("批量数据发送完成: 节点={}, 总大小={}bytes, 延迟={}ms", 
                        targetNode, totalBytes, latency);
                return true;
                
            } catch (Exception e) {
                logger.error("批量发送数据失败: 节点={}", targetNode, e);
                errorCount.incrementAndGet();
                return false;
            }
        }, asyncExecutor);
    }

    @Override
    public void enableConnectionPool(int poolSize) {
        this.connectionPoolSize = poolSize;
        logger.info("连接池已启用: 大小={}", poolSize);
    }

    @Override
    public void enablePipelining(boolean enabled) {
        this.pipeliningEnabled = enabled;
        logger.info("请求管道化已{}: {}", enabled ? "启用" : "禁用", enabled);
    }

    @Override
    public void setNetworkTimeout(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        logger.info("网络超时时间已设置: 连接超时={}ms, 读取超时={}ms", 
                connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public NetworkStats getNetworkStats() {
        // 更新当前统计信息
        networkStats.setTotalBytesSent(totalBytesSent.get());
        networkStats.setTotalBytesReceived(totalBytesReceived.get());
        networkStats.setTotalRequestsSent(totalRequestsSent.get());
        networkStats.setTotalResponsesReceived(totalResponsesReceived.get());
        networkStats.setErrorCount(errorCount.get());
        networkStats.setActiveConnections(connectionPoolSize);
        
        // 计算平均延迟
        double avgLatency = calculateAverageLatency();
        networkStats.setAverageLatency(avgLatency);
        
        return networkStats;
    }

    /**
     * 更新延迟统计
     */
    private void updateLatencyStats(String targetNode, long latency) {
        latencySum.merge(targetNode, latency, Long::sum);
        latencyCount.merge(targetNode, 1L, Long::sum);
    }

    /**
     * 计算平均延迟
     */
    private double calculateAverageLatency() {
        long totalLatency = latencySum.values().stream().mapToLong(Long::longValue).sum();
        long totalCount = latencyCount.values().stream().mapToLong(Long::longValue).sum();
        
        if (totalCount == 0) {
            return 0.0;
        }
        
        return (double) totalLatency / totalCount;
    }

    /**
     * 获取网络配置信息
     */
    public NetworkConfig getNetworkConfig() {
        return new NetworkConfig(connectionPoolSize, pipeliningEnabled, 
                connectTimeoutMs, readTimeoutMs);
    }

    /**
     * 重置网络统计
     */
    public void resetNetworkStats() {
        totalBytesSent.set(0);
        totalBytesReceived.set(0);
        totalRequestsSent.set(0);
        totalResponsesReceived.set(0);
        errorCount.set(0);
        latencySum.clear();
        latencyCount.clear();
        
        logger.info("网络统计信息已重置");
    }

    /**
     * 网络配置类
     */
    public static class NetworkConfig {
        private final int connectionPoolSize;
        private final boolean pipeliningEnabled;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;

        public NetworkConfig(int connectionPoolSize, boolean pipeliningEnabled, 
                           int connectTimeoutMs, int readTimeoutMs) {
            this.connectionPoolSize = connectionPoolSize;
            this.pipeliningEnabled = pipeliningEnabled;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
        }

        // Getters
        public int getConnectionPoolSize() { return connectionPoolSize; }
        public boolean isPipeliningEnabled() { return pipeliningEnabled; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        asyncExecutor.shutdown();
        logger.info("网络优化服务已关闭");
    }
} 