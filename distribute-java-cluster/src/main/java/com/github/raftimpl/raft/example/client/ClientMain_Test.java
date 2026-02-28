package com.github.raftimpl.raft.example.client;

import com.baidu.brpc.client.BrpcProxy;
import com.baidu.brpc.client.RpcClient;
import com.github.raftimpl.raft.example.server.service.ExampleProto;
import com.github.raftimpl.raft.example.server.service.ExampleService;
import com.googlecode.protobuf.format.JsonFormat;

public class ClientMain_Test {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ClientMain_Test <cluster> <key> [value]");
            System.out.println("Example: java ClientMain_Test \"127.0.0.1:8051:1,127.0.0.1:8052:2,127.0.0.1:8053:3\" \"test_key\" \"test_value\"");
            return;
        }

        String cluster = args[0];
        String key = args[1];
        String value = args.length > 2 ? args[2] : null;

        // 解析集群配置，选择第一个节点进行连接
        String[] servers = cluster.split(",");
        String firstServer = servers[0];
        String[] parts = firstServer.split(":");
        String ipport = "list://" + parts[0] + ":" + parts[1];

        // init rpc client
        RpcClient rpcClient = new RpcClient(ipport);

        ExampleService exampleService = BrpcProxy.getProxy(rpcClient, ExampleService.class);
        final JsonFormat jsonFormat = new JsonFormat();

        try {
            // set
            if (value != null) {
                ExampleProto.SetRequest setRequest = ExampleProto.SetRequest.newBuilder()
                        .setKey(key).setValue(value).build();
                ExampleProto.SetResponse setResponse = exampleService.set(setRequest);
                System.out.printf("set request, key=%s value=%s response=%s\n",
                        key, value, jsonFormat.printToString(setResponse));
            } else {
                // get
                ExampleProto.GetRequest getRequest = ExampleProto.GetRequest.newBuilder()
                        .setKey(key).build();
                ExampleProto.GetResponse getResponse = exampleService.get(getRequest);
                System.out.printf("get request, key=%s, response=%s\n",
                        key, jsonFormat.printToString(getResponse));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            rpcClient.stop();
        }
    }
}
