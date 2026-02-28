package com.github.raftimpl.raft.example.server;

import com.baidu.brpc.server.RpcServer;
import com.baidu.brpc.server.RpcServerOptions;
import com.github.raftimpl.raft.ConsensusNode;
import com.github.raftimpl.raft.ConsensusConfiguration;
import com.github.raftimpl.raft.StateMachine;
import com.github.raftimpl.raft.example.server.machine.LevelDBStateMachine;
import com.github.raftimpl.raft.example.server.service.ExampleService;
import com.github.raftimpl.raft.example.server.service.impl.ExampleServiceImpl;
import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.service.RaftClientService;
import com.github.raftimpl.raft.service.impl.RaftClientServiceImpl;
import com.github.raftimpl.raft.service.ConsensusProtocolService;
import com.github.raftimpl.raft.service.ConsensusProtocolServiceAsync;
import com.github.raftimpl.raft.service.impl.ConsensusProtocolServiceImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Raft服务端主程序
 * 负责启动Raft节点，初始化各种服务，并启动RPC服务器
 * 这是一个分布式Raft集群中单个节点的启动入口
 * 
 */
public class ServerMain {
    
    /**
     * 主程序入口
     * 解析命令行参数，初始化RPC服务器、Raft节点和各种服务
     * 
     * @param args 命令行参数: [数据路径] [集群节点列表] [当前节点信息]
     */
    public static void main(String[] args) {
        // 检查参数数量
        if (args.length != 3) {
            System.out.printf("Usage: ./run_server.sh DATA_PATH CLUSTER CURRENT_NODE\n");
            System.exit(-1);
        }
        
        // 解析命令行参数
        System.out.print(args);
        
        // Raft数据存储目录
        String dataPath = args[0];
        
        // 解析集群节点信息，格式: "host:port:serverId,host2:port2:serverId2"
        String servers = args[1];
        String[] splitArray = servers.split(",");
        List<RaftProto.Server> serverList = new ArrayList<>();
        for (String serverString : splitArray) {
            RaftProto.Server server = parseServer(serverString);
            serverList.add(server);
        }
        
        // 解析当前节点信息
        RaftProto.Server localServer = parseServer(args[2]);

        // 初始化RPC服务器配置
        RpcServerOptions options = new RpcServerOptions();
        // 设置IO线程数为CPU核心数的10倍
        options.setIoThreadNum(Runtime.getRuntime().availableProcessors() * 10);
        // 设置工作线程数为CPU核心数的10倍
        options.setWorkThreadNum(Runtime.getRuntime().availableProcessors() * 10);
        RpcServer rpcServer = new RpcServer(localServer.getEndpoint().getPort(), options);
        
        // 设置Raft算法配置选项
        // 这里为了测试快照功能，设置了较小的值
        ConsensusConfiguration consensusConfig = new ConsensusConfiguration();
        consensusConfig.setDataDir(dataPath);                          // 数据目录
        consensusConfig.setSnapshotMinLogSize(10 * 1024);             // 快照最小日志大小: 10KB
        consensusConfig.setBackupIntervalSeconds(30);                 // 快照周期: 30秒
        consensusConfig.setMaxSegmentFileSize(1024 * 1024);          // 最大段文件大小: 1MB
        
        // 初始化状态机（这里使用LevelDB实现）
        StateMachine stateMachine =
        //    new HashMapStateMachine(consensusConfig.getDataDir());     // 内存HashMap实现
                new LevelDBStateMachine(consensusConfig.getDataDir());     // LevelDB持久化实现
//                new BTreeStateMachine(consensusConfig.getDataDir());      // BTree实现
//                new BitCaskStateMachine(consensusConfig.getDataDir());    // BitCask实现
        
        // 初始化Raft节点
        ConsensusNode consensusNode = new ConsensusNode(consensusConfig, serverList, localServer, stateMachine);
        
        // 注册Raft节点之间相互调用的共识服务
        ConsensusProtocolService protocolService = new ConsensusProtocolServiceImpl(consensusNode);
        rpcServer.registerService(protocolService);
        
        // 注册给客户端调用的Raft管理服务
        RaftClientService raftClientService = new RaftClientServiceImpl(consensusNode);
        rpcServer.registerService(raftClientService);
        
        // 注册应用自己提供的业务服务
        ExampleService exampleService = new ExampleServiceImpl(consensusNode, stateMachine);
        rpcServer.registerService(exampleService);
        
        // 启动RPC服务器
        rpcServer.start();
        
        // 初始化Raft节点（启动选举定时器、心跳定时器等）
        consensusNode.init();
    }

    /**
     * 解析服务器字符串为Server对象
     * 
     * @param serverString 服务器信息字符串，格式: "host:port:serverId"
     * @return 解析后的Server对象
     */
    private static RaftProto.Server parseServer(String serverString) {
        String[] splitServer = serverString.split(":");
        String host = splitServer[0];                              // 主机地址
        Integer port = Integer.parseInt(splitServer[1]);           // 端口号
        Integer serverId = Integer.parseInt(splitServer[2]);       // 服务器ID
        
        // 构建端点信息
        RaftProto.Endpoint endPoint = RaftProto.Endpoint.newBuilder()
                .setHost(host).setPort(port).build();
        
        // 构建服务器信息
        RaftProto.Server.Builder serverBuilder = RaftProto.Server.newBuilder();
        RaftProto.Server server = serverBuilder.setServerId(serverId).setEndpoint(endPoint).build();
        return server;
    }
}
