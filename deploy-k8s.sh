#!/bin/bash

# 分布式存储系统Kubernetes部署脚本

set -e

echo "🚀 开始部署分布式存储系统到Kubernetes..."

# 检查kubectl是否可用
if ! command -v kubectl &> /dev/null; then
    echo "❌ kubectl未安装，请先安装kubectl"
    exit 1
fi

# 检查集群连接
if ! kubectl cluster-info &> /dev/null; then
    echo "❌ 无法连接到Kubernetes集群，请检查kubeconfig配置"
    exit 1
fi

echo "✅ Kubernetes集群连接正常"

# 部署顺序
echo "📋 开始按顺序部署资源..."

# 1. 创建命名空间
echo "1️⃣ 创建命名空间..."
kubectl apply -f k8s/namespaces/

# 2. 创建ConfigMaps和Secrets
echo "2️⃣ 创建配置和密钥..."
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/

# 3. 创建PVC
echo "3️⃣ 创建持久化存储..."
kubectl apply -f k8s/deployments/raft-nodes-pvc.yaml

# 4. 部署Redis
echo "4️⃣ 部署Redis服务..."
kubectl apply -f k8s/deployments/redis-deployment.yaml
kubectl apply -f k8s/services/redis-service.yaml

# 等待Redis就绪
echo "⏳ 等待Redis服务就绪..."
kubectl wait --for=condition=available --timeout=300s deployment/redis-deployment -n raft-storage

# 5. 部署Raft节点
echo "5️⃣ 部署Raft节点..."
kubectl apply -f k8s/deployments/raft-nodes-deployment.yaml
kubectl apply -f k8s/services/raft-nodes-services.yaml

# 等待Raft节点就绪
echo "⏳ 等待Raft节点就绪..."
kubectl wait --for=condition=available --timeout=300s deployment/raft-node-1-deployment -n raft-storage
kubectl wait --for=condition=available --timeout=300s deployment/raft-node-2-deployment -n raft-storage
kubectl wait --for=condition=available --timeout=300s deployment/raft-node-3-deployment -n raft-storage

# 6. 部署API服务
echo "6️⃣ 部署API服务..."
kubectl apply -f k8s/deployments/api-deployment.yaml
kubectl apply -f k8s/services/api-service.yaml

# 等待API服务就绪
echo "⏳ 等待API服务就绪..."
kubectl wait --for=condition=available --timeout=300s deployment/raft-api-deployment -n raft-storage

# 7. 部署监控服务
echo "7️⃣ 部署监控服务..."
kubectl apply -f k8s/monitoring/

# 等待监控服务就绪
echo "⏳ 等待监控服务就绪..."
kubectl wait --for=condition=available --timeout=300s deployment/prometheus-deployment -n raft-storage
kubectl wait --for=condition=available --timeout=300s deployment/grafana-deployment -n raft-storage

# 8. 部署Ingress
echo "8️⃣ 部署Ingress..."
kubectl apply -f k8s/ingress/

echo "✅ 部署完成！"

# 显示部署状态
echo ""
echo "📊 部署状态："
kubectl get all -n raft-storage

echo ""
echo "🔗 服务访问地址："
echo "   - API服务: http://raft-api.local"
echo "   - Prometheus: http://prometheus.local"
echo "   - Grafana: http://grafana.local (admin/admin123)"

echo ""
echo "📝 获取服务状态命令："
echo "   kubectl get pods -n raft-storage"
echo "   kubectl get svc -n raft-storage"
echo "   kubectl logs -f deployment/raft-api-deployment -n raft-storage"

echo ""
echo "🧹 清理部署命令："
echo "   kubectl delete namespace raft-storage"
