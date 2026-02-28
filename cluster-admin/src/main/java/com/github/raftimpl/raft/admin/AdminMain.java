package com.github.raftimpl.raft.admin;

import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.service.RaftClientService;
import com.googlecode.protobuf.format.JsonFormat;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;

/**
 * Raft集群管理工具主程序
 * 提供命令行接口用于管理Raft集群，包括查看集群配置、添加节点、删除节点等操作
 * 
 */
public class AdminMain {
    /** JSON格式化工具，用于美化输出 */
    private static final JsonFormat jsonFormat = new JsonFormat();

    /**
     * 主程序入口
     * 解析命令行参数并执行相应的集群管理操作
     * 
     * @param args 命令行参数: [集群节点列表] [操作类型] [子操作] [附加参数]
     */
    public static void main(String[] args) {
        // 检查参数数量
        if (args.length < 3) {
            System.out.println("java -jar AdminMain servers cmd subCmd [args]");
            System.exit(1);
        }
        
        // 解析命令行参数
        // 集群节点列表格式: "10.1.1.1:8010:1,10.2.2.2:8011:2,10.3.3.3.3:8012:3"
        String clusterNodes = args[0];
        String operation = args[1];        // 操作类型，目前只支持"conf"
        String operationType = args[2];    // 子操作类型: get/add/del
        
        // 验证参数合法性
        Validate.isTrue(operation.equals("conf"));
        Validate.isTrue(operationType.equals("get")
                || operationType.equals("add")
                || operationType.equals("del"));
        
        // 创建Raft客户端代理
        RaftClientService raftClient = new RaftClientServiceProxy(clusterNodes);
        
        if (operationType.equals("get")) {
            // 获取集群配置
            RaftProto.GetConfigurationRequest configRequest = RaftProto.GetConfigurationRequest.newBuilder().build();
            RaftProto.GetConfigurationResponse configResponse = raftClient.getConfig(configRequest);
            if (configResponse != null) {
                // 以JSON格式输出集群配置信息
                System.out.println(jsonFormat.printToString(configResponse));
            } else {
                System.out.printf("response == null");
            }

        } else if (operationType.equals("add")) {
            // 添加节点到集群
            List<RaftProto.Server> nodeList = parseStorageServers(args[3]);
            RaftProto.AddPeersRequest addRequest = RaftProto.AddPeersRequest.newBuilder()
                    .addAllServers(nodeList).build();
            RaftProto.AddPeersResponse addResponse = raftClient.addStoragePeers(addRequest);
            if (addResponse != null) {
                // 输出操作结果码
                System.out.println(addResponse.getResCode());
            } else {
                System.out.printf("response == null");
            }
        } else if (operationType.equals("del")) {
            // 从集群中删除节点
            List<RaftProto.Server> nodeList = parseStorageServers(args[3]);
            RaftProto.RemovePeersRequest removeRequest = RaftProto.RemovePeersRequest.newBuilder()
                    .addAllServers(nodeList).build();
            RaftProto.RemovePeersResponse removeResponse = raftClient.removeStoragePeers(removeRequest);
            if (removeResponse != null) {
                // 输出操作结果码
                System.out.println(removeResponse.getResCode());
            } else {
                System.out.printf("response == null");
            }
        }
        
        // 关闭客户端连接
        ((RaftClientServiceProxy) raftClient).stop();
    }

    /**
     * 解析节点信息字符串为Server对象列表
     * 
     * @param nodesInfo 节点信息字符串，格式: "host1:port1:id1,host2:port2:id2,..."
     * @return 解析后的Server对象列表
     */
    public static List<RaftProto.Server> parseStorageServers(String nodesInfo) {
        List<RaftProto.Server> nodeList = new ArrayList<>();
        String[] addressArray = nodesInfo.split(",");
        
        // 遍历每个节点地址并解析
        for (String nodeAddr : addressArray) {
            String[] nodeInfo = nodeAddr.split(":");
            
            // 构建节点端点信息
            RaftProto.Endpoint nodeEndpoint = RaftProto.Endpoint.newBuilder()
                    .setHost(nodeInfo[0])                           // 主机地址
                    .setPort(Integer.parseInt(nodeInfo[1])).build(); // 端口号
                    
            // 构建节点服务器信息
            RaftProto.Server nodeServer = RaftProto.Server.newBuilder()
                    .setEndpoint(nodeEndpoint)
                    .setServerId(Integer.parseInt(nodeInfo[2])).build(); // 服务器ID
                    
            nodeList.add(nodeServer);
        }
        return nodeList;
    }
}
