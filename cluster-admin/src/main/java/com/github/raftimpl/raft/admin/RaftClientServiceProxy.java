package com.github.raftimpl.raft.admin;

import com.baidu.brpc.client.BrpcProxy;
import com.baidu.brpc.client.RpcClient;
import com.baidu.brpc.client.RpcClientOptions;
import com.baidu.brpc.client.instance.Endpoint;
import com.github.raftimpl.raft.proto.RaftProto;
import com.github.raftimpl.raft.service.RaftClientService;
import com.googlecode.protobuf.format.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RaftClientServiceProxy implements RaftClientService {
    private static final Logger LOG = LoggerFactory.getLogger(RaftClientServiceProxy.class);
    private static final JsonFormat jsonFormat = new JsonFormat();

    private List<RaftProto.Server> nodeGroup;
    private RpcClient nodeGroupClient;
    private RaftClientService nodeGroupService;

    private RaftProto.Server masterNode;
    private RpcClient masterClient;
    private RaftClientService masterService;

    private RpcClientOptions clientConfig = new RpcClientOptions();

    // servers format is 10.1.1.1:8888,10.2.2.2:9999
    public RaftClientServiceProxy(String addresses) {
        clientConfig.setConnectTimeoutMillis(1000); // 1s
        clientConfig.setReadTimeoutMillis(3600000); // 1hour
        clientConfig.setWriteTimeoutMillis(1000); // 1s
        nodeGroupClient = new RpcClient(addresses, clientConfig);
        nodeGroupService = BrpcProxy.getProxy(nodeGroupClient, RaftClientService.class);
        refreshConfig();
    }

    @Override
    public RaftProto.GetLeaderResponse getNowLeader(RaftProto.GetLeaderRequest request) {
        return nodeGroupService.getNowLeader(request);
    }

    @Override
    public RaftProto.GetConfigurationResponse getConfig(RaftProto.GetConfigurationRequest request) {
        return nodeGroupService.getConfig(request);
    }

    @Override
    public RaftProto.AddPeersResponse addStoragePeers(RaftProto.AddPeersRequest request) {
        RaftProto.AddPeersResponse reply = masterService.addStoragePeers(request);
        if (reply != null && reply.getResCode() == RaftProto.ResCode.RES_CODE_NOT_LEADER) {
            refreshConfig();
            reply = masterService.addStoragePeers(request);
        }
        return reply;
    }

    @Override
    public RaftProto.RemovePeersResponse removeStoragePeers(RaftProto.RemovePeersRequest request) {
        RaftProto.RemovePeersResponse reply = masterService.removeStoragePeers(request);
        if (reply != null && reply.getResCode() == RaftProto.ResCode.RES_CODE_NOT_LEADER) {
            refreshConfig();
            reply = masterService.removeStoragePeers(request);
        }
        return reply;
    }

    public void stop() {
        if (masterClient != null) {
            masterClient.stop();
        }
        if (nodeGroupClient != null) {
            nodeGroupClient.stop();
        }
    }

    private boolean refreshConfig() {
        RaftProto.GetConfigurationRequest configReq = RaftProto.GetConfigurationRequest.newBuilder().build();
        RaftProto.GetConfigurationResponse configResp = nodeGroupService.getConfig(configReq);
        if (configResp != null && configResp.getResCode() == RaftProto.ResCode.RES_CODE_SUCCESS) {
            if (masterClient != null) {
                masterClient.stop();
            }
            masterNode = configResp.getNowLeader();
            masterClient = new RpcClient(toEndpoint(masterNode.getEndpoint()), clientConfig);
            masterService = BrpcProxy.getProxy(masterClient, RaftClientService.class);
            return true;
        }
        return false;
    }

    private Endpoint toEndpoint(RaftProto.Endpoint nodeEndpoint) {
        return new Endpoint(nodeEndpoint.getHost(), nodeEndpoint.getPort());
    }
}
