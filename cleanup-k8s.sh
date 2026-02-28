#!/bin/bash

# 分布式存储系统Kubernetes清理脚本

set -e

echo "🧹 开始清理Kubernetes部署..."

# 检查kubectl是否可用
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl未安装"
    exit 1
fi

# 检查命名空间是否存在
if kubectl get namespace raft-storage &> /dev/null; then
    echo "🗑️ 删除raft-storage命名空间及所有资源..."
    kubectl delete namespace raft-storage
    
    echo "⏳ 等待资源清理完成..."
    while kubectl get namespace raft-storage &> /dev/null; do
        echo "   等待命名空间删除..."
        sleep 5
    done
    
    echo "✅ 清理完成！"
else
    echo "ℹ️ raft-storage命名空间不存在，无需清理"
fi

echo ""
echo "🔍 验证清理结果："
kubectl get namespaces | grep raft-storage || echo "   ✅ raft-storage命名空间已删除"
