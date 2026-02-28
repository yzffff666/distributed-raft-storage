package com.github.raftimpl.raft.util;

import com.github.raftimpl.raft.proto.RaftProto;

import java.util.List;

/**
 * 集群配置工具类
 * 提供节点查找、添加、删除等功能
 * 
 */
public class ConfigurationUtils {

    // configuration不会太大，所以这里直接遍历了
    public static boolean containsNode(RaftProto.Configuration configuration, int serverId) {
        for (RaftProto.Server node : configuration.getServersList()) {
            if (node.getServerId() == serverId) {
                return true;
            }
        }
        return false;
    }

    public static RaftProto.Configuration removeNodes(
            RaftProto.Configuration configuration, List<RaftProto.Server> nodesToRemove) {
        RaftProto.Configuration.Builder configBuilder = RaftProto.Configuration.newBuilder();
        for (RaftProto.Server currentNode : configuration.getServersList()) {
            boolean shouldRemove = false;
            for (RaftProto.Server targetNode : nodesToRemove) {
                if (currentNode.getServerId() == targetNode.getServerId()) {
                    shouldRemove = true;
                    break;
                }
            }
            if (!shouldRemove) {
                configBuilder.addServers(currentNode);
            }
        }
        return configBuilder.build();
    }

    public static RaftProto.Server getNodeById(RaftProto.Configuration configuration, int serverId) {
        for (RaftProto.Server node : configuration.getServersList()) {
            if (node.getServerId() == serverId) {
                return node;
            }
        }
        return null;
    }

    // 保持向后兼容的方法名，委托给新方法
    public static boolean containsStorageServer(RaftProto.Configuration configuration, int serverId) {
        return containsNode(configuration, serverId);
    }

    public static RaftProto.Configuration removeStorageServers(
            RaftProto.Configuration configuration, List<RaftProto.Server> nodesToRemove) {
        return removeNodes(configuration, nodesToRemove);
    }

    public static RaftProto.Server getStorageServer(RaftProto.Configuration configuration, int serverId) {
        return getNodeById(configuration, serverId);
    }
}
