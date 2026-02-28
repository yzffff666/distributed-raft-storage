#!/bin/bash

# Helm卸载脚本
set -e

RELEASE_NAME="raft-storage"
NAMESPACE="raft-storage"

echo "=== Raft Storage Helm Cleanup Script ==="

# 检查Helm是否安装
if ! command -v helm &> /dev/null; then
    echo "❌ Helm is not installed."
    exit 1
fi

# 检查kubectl是否安装
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed."
    exit 1
fi

# 检查发布是否存在
if ! helm list --namespace $NAMESPACE | grep -q $RELEASE_NAME; then
    echo "⚠️  Release $RELEASE_NAME not found in namespace $NAMESPACE"
    echo "Available releases:"
    helm list --namespace $NAMESPACE
    exit 0
fi

echo "🔍 Found release: $RELEASE_NAME in namespace: $NAMESPACE"

# 显示当前状态
echo ""
echo "📊 Current Status:"
helm status $RELEASE_NAME --namespace $NAMESPACE

# 提示用户确认
echo ""
echo "⚠️  This will completely remove the Raft Storage deployment"
echo "   Release: $RELEASE_NAME"
echo "   Namespace: $NAMESPACE"
echo "   ⚠️  All data will be lost!"
echo ""
read -p "Are you sure you want to proceed? (y/N): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🗑️  Uninstalling Raft Storage..."
    
    # 卸载Helm发布
    helm uninstall $RELEASE_NAME --namespace $NAMESPACE
    
    echo "✅ Helm release uninstalled successfully!"
    
    # 询问是否删除命名空间
    echo ""
    read -p "Do you want to delete the namespace '$NAMESPACE' as well? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🗑️  Deleting namespace..."
        kubectl delete namespace $NAMESPACE --ignore-not-found=true
        echo "✅ Namespace deleted successfully!"
    else
        echo "ℹ️  Namespace '$NAMESPACE' preserved"
        echo "   You can delete it manually with: kubectl delete namespace $NAMESPACE"
    fi
    
    # 询问是否删除PVC
    echo ""
    read -p "Do you want to delete all PVCs (this will delete all data)? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "��️  Deleting PVCs..."
        kubectl delete pvc --all --namespace $NAMESPACE --ignore-not-found=true
        echo "✅ PVCs deleted successfully!"
    else
        echo "ℹ️  PVCs preserved"
        echo "   You can delete them manually with: kubectl delete pvc --all --namespace $NAMESPACE"
    fi
    
else
    echo "❌ Cleanup cancelled by user"
    exit 0
fi

echo ""
echo "🎉 Raft Storage cleanup completed!"
echo "📝 Remaining resources (if any):"
kubectl get all --namespace $NAMESPACE 2>/dev/null || echo "   No resources found"
