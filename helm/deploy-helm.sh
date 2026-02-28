#!/bin/bash

# Helm部署脚本
set -e

CHART_NAME="raft-storage"
RELEASE_NAME="raft-storage"
NAMESPACE="raft-storage"
CHART_PATH="./raft-storage"

echo "=== Raft Storage Helm Deployment Script ==="

# 检查Helm是否安装
if ! command -v helm &> /dev/null; then
    echo "❌ Helm is not installed. Please install Helm first."
    echo "Visit: https://helm.sh/docs/intro/install/"
    exit 1
fi

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

echo "✅ Prerequisites check passed"

# 添加必要的Helm仓库
echo "�� Adding Helm repositories..."
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

echo "✅ Helm repositories updated"

# 创建命名空间（如果不存在）
echo "🏗️  Creating namespace if not exists..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# 验证Chart语法
echo "🔍 Validating Helm chart..."
helm lint $CHART_PATH

# 模拟部署（dry-run）
echo "🧪 Running dry-run deployment..."
helm upgrade --install $RELEASE_NAME $CHART_PATH \
    --namespace $NAMESPACE \
    --dry-run \
    --debug

# 提示用户确认
echo ""
echo "🚀 Ready to deploy Raft Storage to Kubernetes cluster"
echo "   Release: $RELEASE_NAME"
echo "   Namespace: $NAMESPACE"
echo "   Chart: $CHART_PATH"
echo ""
read -p "Do you want to proceed with the deployment? (y/N): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🚀 Deploying Raft Storage..."
    
    # 执行部署
    helm upgrade --install $RELEASE_NAME $CHART_PATH \
        --namespace $NAMESPACE \
        --create-namespace \
        --wait \
        --timeout 10m
    
    echo "✅ Deployment completed successfully!"
    
    # 显示部署状态
    echo ""
    echo "📊 Deployment Status:"
    helm status $RELEASE_NAME --namespace $NAMESPACE
    
    echo ""
    echo "🔍 Pod Status:"
    kubectl get pods --namespace $NAMESPACE
    
    echo ""
    echo "🌐 Service Status:"
    kubectl get services --namespace $NAMESPACE
    
    echo ""
    echo "📝 Access Information:"
    echo "   API Service: kubectl port-forward --namespace $NAMESPACE svc/raft-storage-api 8080:80"
    echo "   Prometheus: kubectl port-forward --namespace $NAMESPACE svc/raft-storage-prometheus-server 9090:80"
    echo "   Grafana: kubectl port-forward --namespace $NAMESPACE svc/raft-storage-grafana 3000:80"
    
else
    echo "❌ Deployment cancelled by user"
    exit 0
fi

echo ""
echo "🎉 Raft Storage deployment completed!"
echo "📚 For more information, run: helm get notes $RELEASE_NAME --namespace $NAMESPACE"
