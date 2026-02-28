package com.github.raftimpl.raft.api.service;

import com.github.raftimpl.raft.api.dto.NodeInfo;
import java.util.List;
import java.util.Map;

/**
 * 智能路由服务接口
 * 提供智能路由和就近访问功能，优化网络性能
 * 
 */
public interface SmartRoutingService {

    /**
     * 根据客户端位置选择最优节点
     * 
     * @param clientLocation 客户端位置信息
     * @param operation 操作类型
     * @return 最优节点
     */
    NodeInfo selectOptimalNode(ClientLocation clientLocation, OperationType operation);

    /**
     * 获取客户端的就近节点列表
     * 
     * @param clientLocation 客户端位置信息
     * @param maxNodes 最大节点数
     * @return 就近节点列表
     */
    List<NodeInfo> getNearbyNodes(ClientLocation clientLocation, int maxNodes);

    /**
     * 更新节点地理位置信息
     * 
     * @param nodeId 节点ID
     * @param location 地理位置
     */
    void updateNodeLocation(String nodeId, GeographicLocation location);

    /**
     * 计算两点之间的距离
     * 
     * @param location1 位置1
     * @param location2 位置2
     * @return 距离（公里）
     */
    double calculateDistance(GeographicLocation location1, GeographicLocation location2);

    /**
     * 获取网络延迟信息
     * 
     * @param sourceNode 源节点
     * @param targetNode 目标节点
     * @return 网络延迟（毫秒）
     */
    double getNetworkLatency(String sourceNode, String targetNode);

    /**
     * 更新网络延迟信息
     * 
     * @param sourceNode 源节点
     * @param targetNode 目标节点
     * @param latency 延迟（毫秒）
     */
    void updateNetworkLatency(String sourceNode, String targetNode, double latency);

    /**
     * 获取路由统计信息
     * 
     * @return 路由统计
     */
    RoutingStats getRoutingStats();

    /**
     * 客户端位置信息
     */
    class ClientLocation {
        private String clientId;
        private String ipAddress;
        private GeographicLocation geographicLocation;
        private String region;
        private String isp; // 网络服务提供商
        private long timestamp;

        // 构造函数
        public ClientLocation() {}

        public ClientLocation(String clientId, String ipAddress) {
            this.clientId = clientId;
            this.ipAddress = ipAddress;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and Setters
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public GeographicLocation getGeographicLocation() { return geographicLocation; }
        public void setGeographicLocation(GeographicLocation geographicLocation) { this.geographicLocation = geographicLocation; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getIsp() { return isp; }
        public void setIsp(String isp) { this.isp = isp; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * 地理位置信息
     */
    class GeographicLocation {
        private double latitude;  // 纬度
        private double longitude; // 经度
        private String country;
        private String province;
        private String city;

        // 构造函数
        public GeographicLocation() {}

        public GeographicLocation(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public GeographicLocation(double latitude, double longitude, String country, String province, String city) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.country = country;
            this.province = province;
            this.city = city;
        }

        // Getters and Setters
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }

        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }

        public String getProvince() { return province; }
        public void setProvince(String province) { this.province = province; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        @Override
        public String toString() {
            return String.format("GeographicLocation{lat=%.6f, lng=%.6f, country='%s', province='%s', city='%s'}", 
                    latitude, longitude, country, province, city);
        }
    }

    /**
     * 操作类型枚举
     */
    enum OperationType {
        READ,   // 读操作
        write,  // 写操作
        mixed   // 混合操作
    }

    /**
     * 路由策略枚举
     */
    enum RoutingStrategy {
        GEOGRAPHIC_DISTANCE,  // 地理距离优先
        NETWORK_LATENCY,      // 网络延迟优先
        LOAD_BALANCED,        // 负载均衡
        HYBRID               // 混合策略
    }

    /**
     * 路由统计信息
     */
    class RoutingStats {
        private long totalRoutingRequests;
        private long successfulRoutings;
        private long failedRoutings;
        private double averageRoutingTime;
        private Map<String, Long> routingByRegion;
        private Map<String, Long> routingByStrategy;
        private Map<String, Double> averageLatencyByRegion;

        // 构造函数
        public RoutingStats() {}

        // Getters and Setters
        public long getTotalRoutingRequests() { return totalRoutingRequests; }
        public void setTotalRoutingRequests(long totalRoutingRequests) { this.totalRoutingRequests = totalRoutingRequests; }

        public long getSuccessfulRoutings() { return successfulRoutings; }
        public void setSuccessfulRoutings(long successfulRoutings) { this.successfulRoutings = successfulRoutings; }

        public long getFailedRoutings() { return failedRoutings; }
        public void setFailedRoutings(long failedRoutings) { this.failedRoutings = failedRoutings; }

        public double getAverageRoutingTime() { return averageRoutingTime; }
        public void setAverageRoutingTime(double averageRoutingTime) { this.averageRoutingTime = averageRoutingTime; }

        public Map<String, Long> getRoutingByRegion() { return routingByRegion; }
        public void setRoutingByRegion(Map<String, Long> routingByRegion) { this.routingByRegion = routingByRegion; }

        public Map<String, Long> getRoutingByStrategy() { return routingByStrategy; }
        public void setRoutingByStrategy(Map<String, Long> routingByStrategy) { this.routingByStrategy = routingByStrategy; }

        public Map<String, Double> getAverageLatencyByRegion() { return averageLatencyByRegion; }
        public void setAverageLatencyByRegion(Map<String, Double> averageLatencyByRegion) { this.averageLatencyByRegion = averageLatencyByRegion; }

        /**
         * 计算路由成功率
         * 
         * @return 成功率（0-1之间）
         */
        public double getSuccessRate() {
            if (totalRoutingRequests <= 0) return 0.0;
            return (double) successfulRoutings / totalRoutingRequests;
        }
    }
} 