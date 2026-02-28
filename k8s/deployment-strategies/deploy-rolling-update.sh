#!/bin/bash

# 滚动更新部署脚本
set -e

NAMESPACE="raft-storage"
NEW_VERSION="${1:-v1.10.0}"
DEPLOYMENT_NAME="${2:-raft-api-deployment}"
IMAGE_NAME="${3:-raft-api}"

echo "=== Raft Storage Rolling Update Deployment Script ==="
echo "Namespace: $NAMESPACE"
echo "New Version: $NEW_VERSION"
echo "Deployment: $DEPLOYMENT_NAME"
echo "Image: $IMAGE_NAME:$NEW_VERSION"

# 检查kubectl是否安装
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl is not installed. Please install kubectl first."
    exit 1
fi

# 检查Kubernetes集群连接
if ! kubectl cluster-info &> /dev/null; then
    echo "❌ Cannot connect to Kubernetes cluster. Please check your kubeconfig."
    exit 1
fi

# 检查命名空间是否存在
if ! kubectl get namespace $NAMESPACE &> /dev/null; then
    echo "❌ Namespace $NAMESPACE does not exist. Please deploy the main application first."
    exit 1
fi

# 检查部署是否存在
if ! kubectl get deployment $DEPLOYMENT_NAME -n $NAMESPACE &> /dev/null; then
    echo "❌ Deployment $DEPLOYMENT_NAME does not exist in namespace $NAMESPACE."
    echo "Available deployments:"
    kubectl get deployments -n $NAMESPACE
    exit 1
fi

echo "✅ Prerequisites check passed"

# 获取当前版本信息
CURRENT_IMAGE=$(kubectl get deployment $DEPLOYMENT_NAME -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}')
CURRENT_REPLICAS=$(kubectl get deployment $DEPLOYMENT_NAME -n $NAMESPACE -o jsonpath='{.spec.replicas}')

echo "📊 Current Status:"
echo "   Current Image: $CURRENT_IMAGE"
echo "   Current Replicas: $CURRENT_REPLICAS"

# 显示当前Pod状态
echo ""
echo "📋 Current Pods:"
kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api

# 确认是否继续
echo ""
read -p "🚀 Do you want to proceed with rolling update to $IMAGE_NAME:$NEW_VERSION? (y/N): " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Rolling update cancelled by user"
    exit 0
fi

# 设置更新原因注解
CHANGE_CAUSE="Rolling update to $IMAGE_NAME:$NEW_VERSION at $(date)"
kubectl annotate deployment $DEPLOYMENT_NAME -n $NAMESPACE deployment.kubernetes.io/change-cause="$CHANGE_CAUSE"

# 执行滚动更新
echo "🔄 Starting rolling update..."
kubectl set image deployment/$DEPLOYMENT_NAME -n $NAMESPACE api=$IMAGE_NAME:$NEW_VERSION

# 等待滚动更新完成
echo "⏳ Waiting for rollout to complete..."
if kubectl rollout status deployment/$DEPLOYMENT_NAME -n $NAMESPACE --timeout=600s; then
    echo "✅ Rolling update completed successfully!"
else
    echo "❌ Rolling update failed or timed out"
    
    # 显示失败信息
    echo ""
    echo "📊 Rollout Status:"
    kubectl rollout status deployment/$DEPLOYMENT_NAME -n $NAMESPACE
    
    echo ""
    echo "📋 Pod Status:"
    kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api
    
    echo ""
    echo "🔍 Recent Events:"
    kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -10
    
    # 询问是否回滚
    echo ""
    read -p "🔄 Do you want to rollback to the previous version? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🔙 Rolling back to previous version..."
        kubectl rollout undo deployment/$DEPLOYMENT_NAME -n $NAMESPACE
        
        echo "⏳ Waiting for rollback to complete..."
        kubectl rollout status deployment/$DEPLOYMENT_NAME -n $NAMESPACE --timeout=300s
        
        echo "✅ Rollback completed"
    fi
    
    exit 1
fi

# 验证部署状态
echo ""
echo "📊 Post-deployment Status:"
echo "=========================="

# 检查Pod状态
echo ""
echo "📋 Pod Status:"
kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api -o wide

# 检查服务状态
echo ""
echo "🌐 Service Status:"
kubectl get services -n $NAMESPACE -l app=raft-storage,component=api

# 检查部署历史
echo ""
echo "📜 Deployment History:"
kubectl rollout history deployment/$DEPLOYMENT_NAME -n $NAMESPACE

# 健康检查
echo ""
echo "🏥 Health Check:"
echo "==============="

# 等待Pod就绪
echo "⏳ Waiting for pods to be ready..."
kubectl wait --for=condition=ready pod -l app=raft-storage,component=api -n $NAMESPACE --timeout=300s

# 检查API健康状态
echo "🔍 Checking API health..."
if kubectl get service raft-api-service -n $NAMESPACE &> /dev/null; then
    # 通过Service检查健康状态
    kubectl run health-check --rm -i --tty --restart=Never --image=curlimages/curl -- \
        curl -f http://raft-api-service.$NAMESPACE.svc.cluster.local/api/v1/monitoring/health || \
        echo "⚠️  Health check failed, but this might be normal during startup"
else
    echo "⚠️  Service raft-api-service not found, skipping health check"
fi

# 显示访问信息
echo ""
echo "🎉 Rolling update completed successfully!"
echo ""
echo "📝 Access Information:"
echo "====================="
echo ""
echo "1. Check pod logs:"
echo "   kubectl logs -f deployment/$DEPLOYMENT_NAME -n $NAMESPACE"
echo ""
echo "2. Port forward for testing:"
echo "   kubectl port-forward service/raft-api-service -n $NAMESPACE 8080:80"
echo "   curl http://localhost:8080/api/v1/monitoring/health"
echo ""
echo "3. Monitor deployment:"
echo "   kubectl get pods -n $NAMESPACE -w"
echo ""
echo "4. Rollback if needed:"
echo "   kubectl rollout undo deployment/$DEPLOYMENT_NAME -n $NAMESPACE"
echo ""
echo "5. Check rollout history:"
echo "   kubectl rollout history deployment/$DEPLOYMENT_NAME -n $NAMESPACE" 