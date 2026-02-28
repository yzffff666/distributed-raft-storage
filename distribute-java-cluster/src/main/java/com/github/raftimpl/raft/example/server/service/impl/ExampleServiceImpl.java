package com.github.raftimpl.raft.example.server.service.impl;

import com.baidu.brpc.client.BrpcProxy;
import com.baidu.brpc.client.RpcClient;
import com.baidu.brpc.client.RpcClientOptions;
import com.baidu.brpc.client.instance.Endpoint;
import com.github.raftimpl.raft.ClusterNode;
import com.github.raftimpl.raft.ConsensusNode;
import com.github.raftimpl.raft.StateMachine;
import com.github.raftimpl.raft.example.server.service.ExampleProto;
import com.github.raftimpl.raft.example.server.service.ExampleService;
import com.github.raftimpl.raft.proto.RaftProto;
import com.googlecode.protobuf.format.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实例服务端实现
 */
public class ExampleServiceImpl implements ExampleService {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleServiceImpl.class);
    private static final JsonFormat jsonFormat = new JsonFormat();

    private ConsensusNode raftNode;
    private StateMachine stateMachine;

    private RpcClient leaderRpcClient = null;
    private ExampleService leaderService = null;

    public ExampleServiceImpl(ConsensusNode raftNode, StateMachine stateMachine) {
        this.raftNode = raftNode;
        this.stateMachine = stateMachine;
    }

    private void onLeaderChangeEvent() {
        if (leaderRpcClient != null) {
            leaderRpcClient.stop();
            leaderRpcClient = null;
            leaderService = null;
        }
        if (raftNode.getLeaderId() != -1 && raftNode.getLeaderId() != raftNode.getLocalServer().getServerId()) {
            int leaderId = raftNode.getLeaderId();
            ClusterNode peer = raftNode.getPeerMap().get(leaderId);
            Endpoint endpoint = new Endpoint(peer.getStorageServer().getEndpoint().getHost(),
                    peer.getStorageServer().getEndpoint().getPort());
            RpcClientOptions rpcClientOptions = new RpcClientOptions();
            rpcClientOptions.setConnectTimeoutMillis(1000);
            rpcClientOptions.setReadTimeoutMillis(3000);
            rpcClientOptions.setWriteTimeoutMillis(3000);
            leaderRpcClient = new RpcClient(endpoint, rpcClientOptions);
            leaderService = BrpcProxy.getProxy(leaderRpcClient, ExampleService.class);
        }
    }

    @Override
    public ExampleProto.SetResponse set(ExampleProto.SetRequest request) {
        ExampleProto.SetResponse.Builder responseBuilder = ExampleProto.SetResponse.newBuilder();
        // 如果自己不是leader，将写请求转发给leader
        if (raftNode.getLeaderId() <= 0) {
            responseBuilder.setSuccess(false);
        } else if (raftNode.getLeaderId() != raftNode.getLocalServer().getServerId()) {
            onLeaderChangeEvent();
            ExampleProto.SetResponse responseFromLeader = leaderService.set(request);
            responseBuilder.mergeFrom(responseFromLeader);
        } else {
            // 数据同步写入raft集群
            byte[] data = request.toByteArray();
            boolean success = raftNode.replicate(data, RaftProto.EntryType.ENTRY_TYPE_DATA);

            responseBuilder.setSuccess(success);
        }

        ExampleProto.SetResponse response = responseBuilder.build();
        LOG.info("set request, request={}, response={}", jsonFormat.printToString(request),
                jsonFormat.printToString(response));
        return response;
    }

    @Override
    public ExampleProto.GetResponse get(ExampleProto.GetRequest request) {
        // Follower-read 非强一致性
        ExampleProto.GetResponse.Builder responseBuilder = ExampleProto.GetResponse.newBuilder();
        byte[] keyBytes = request.getKey().getBytes();
        // 从Leader节点获取Read Index，并等待Read Index之前的日志条目应用到复制状态机
        if (raftNode.waitForLeaderCommitIndex()) {
            byte[] valueBytes = stateMachine.get(keyBytes);
            if (valueBytes != null) {
                String value = new String(valueBytes);
                responseBuilder.setValue(value);
            }
        } else {
            LOG.warn("read failed, meet error");
        }
        ExampleProto.GetResponse response = responseBuilder.build();
        LOG.info("get request, request={}, response={}", jsonFormat.printToString(request),
                jsonFormat.printToString(response));
        return response;
    }
}
