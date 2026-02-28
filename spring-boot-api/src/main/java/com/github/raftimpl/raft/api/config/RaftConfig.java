package com.github.raftimpl.raft.api.config;

import com.github.raftimpl.raft.example.server.service.ExampleProto;
import com.github.raftimpl.raft.example.server.service.ExampleService;
import com.github.raftimpl.raft.service.RaftClientService;
import com.github.raftimpl.raft.proto.RaftProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Raft配置类
 * 提供Raft相关的Bean配置
 */
@Configuration
public class RaftConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RaftConfig.class);
    
    /**
     * Mock ExampleService实现
     * 用于API服务启动，实际的Raft功能需要连接到独立的Raft节点
     */
    @Bean
    public ExampleService exampleService() {
        logger.info("创建Mock ExampleService，用于API服务启动");
        return new MockExampleService();
    }
    
    /**
     * Mock RaftClientService实现
     * 用于API服务启动，实际的Raft功能需要连接到独立的Raft节点
     */
    @Bean
    public RaftClientService raftClientService() {
        logger.info("创建Mock RaftClientService，用于API服务启动");
        return new MockRaftClientService();
    }
    
    /**
     * Mock ExampleService实现类
     * 模拟Raft存储操作，实际生产环境需要连接到真实的Raft集群
     */
    private static class MockExampleService implements ExampleService {
        
        private static final Logger logger = LoggerFactory.getLogger(MockExampleService.class);
        
        @Override
        public ExampleProto.SetResponse set(ExampleProto.SetRequest request) {
            logger.info("Mock set operation: key={}, value={}", 
                       request.getKey(), request.getValue());
            
            // 模拟成功响应
            return ExampleProto.SetResponse.newBuilder()
                    .setSuccess(true)
                    .build();
        }
        
        @Override
        public ExampleProto.GetResponse get(ExampleProto.GetRequest request) {
            logger.info("Mock get operation: key={}", request.getKey());
            
            // 模拟响应，返回空值表示数据不存在
            return ExampleProto.GetResponse.newBuilder()
                    .setValue("")
                    .build();
        }
    }
    
    /**
     * Mock RaftClientService实现类
     * 模拟Raft集群管理操作，实际生产环境需要连接到真实的Raft集群
     */
    private static class MockRaftClientService implements RaftClientService {
        
        private static final Logger logger = LoggerFactory.getLogger(MockRaftClientService.class);
        
        @Override
        public RaftProto.GetLeaderResponse getNowLeader(RaftProto.GetLeaderRequest request) {
            logger.info("Mock getNowLeader operation");
            
            // 模拟Leader响应
            RaftProto.Endpoint endpoint = RaftProto.Endpoint.newBuilder()
                    .setHost("localhost")
                    .setPort(8051)
                    .build();
            
            return RaftProto.GetLeaderResponse.newBuilder()
                    .setResCode(RaftProto.ResCode.RES_CODE_SUCCESS)
                    .setLeader(endpoint)
                    .build();
        }
        
        @Override
        public RaftProto.GetConfigurationResponse getConfig(RaftProto.GetConfigurationRequest request) {
            logger.info("Mock getConfig operation");
            
            // 模拟集群配置响应
            RaftProto.Endpoint endpoint1 = RaftProto.Endpoint.newBuilder()
                    .setHost("localhost")
                    .setPort(8051)
                    .build();
            
            RaftProto.Endpoint endpoint2 = RaftProto.Endpoint.newBuilder()
                    .setHost("localhost")
                    .setPort(8052)
                    .build();
            
            RaftProto.Endpoint endpoint3 = RaftProto.Endpoint.newBuilder()
                    .setHost("localhost")
                    .setPort(8053)
                    .build();
            
            RaftProto.Server server1 = RaftProto.Server.newBuilder()
                    .setServerId(1)
                    .setEndpoint(endpoint1)
                    .build();
            
            RaftProto.Server server2 = RaftProto.Server.newBuilder()
                    .setServerId(2)
                    .setEndpoint(endpoint2)
                    .build();
            
            RaftProto.Server server3 = RaftProto.Server.newBuilder()
                    .setServerId(3)
                    .setEndpoint(endpoint3)
                    .build();
            
            return RaftProto.GetConfigurationResponse.newBuilder()
                    .setResCode(RaftProto.ResCode.RES_CODE_SUCCESS)
                    .setLeader(server1) // 使用setLeader而不是setNowLeader
                    .addServers(server1)
                    .addServers(server2)
                    .addServers(server3)
                    .build();
        }
        
        @Override
        public RaftProto.AddPeersResponse addStoragePeers(RaftProto.AddPeersRequest request) {
            logger.info("Mock addStoragePeers operation: servers={}", request.getServersList().size());
            
            return RaftProto.AddPeersResponse.newBuilder()
                    .setResCode(RaftProto.ResCode.RES_CODE_SUCCESS)
                    .build();
        }
        
        @Override
        public RaftProto.RemovePeersResponse removeStoragePeers(RaftProto.RemovePeersRequest request) {
            logger.info("Mock removeStoragePeers operation: servers={}", request.getServersList().size());
            
            return RaftProto.RemovePeersResponse.newBuilder()
                    .setResCode(RaftProto.ResCode.RES_CODE_SUCCESS)
                    .build();
        }
    }
} 