#!/bin/bash

# 蓝绿部署脚本
set -e

NAMESPACE="raft-storage"
NEW_VERSION="${1:-v1.10.0}"
IMAGE_NAME="${2:-raft-api}"
CURRENT_ENV="${3:-blue}"  # 当前生产环境
TARGET_ENV="${4:-green}"  # 目标环境

echo "=== Raft Storage Blue-Green Deployment Script ==="
echo "Namespace: $NAMESPACE"
echo "New Version: $NEW_VERSION"
echo "Image: $IMAGE_NAME:$NEW_VERSION"
echo "Current Environment: $CURRENT_ENV"
echo "Target Environment: $TARGET_ENV"

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

echo "✅ Prerequisites check passed"

# 获取当前生产环境状态
CURRENT_DEPLOYMENT="raft-api-$CURRENT_ENV"
TARGET_DEPLOYMENT="raft-api-$TARGET_ENV"
PRODUCTION_SERVICE="raft-api-service"
TEST_SERVICE="raft-api-service-test"

# 检查当前部署状态
if kubectl get deployment $CURRENT_DEPLOYMENT -n $NAMESPACE &> /dev/null; then
    CURRENT_IMAGE=$(kubectl get deployment $CURRENT_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}')
    CURRENT_REPLICAS=$(kubectl get deployment $CURRENT_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.replicas}')
    echo "📊 Current Production ($CURRENT_ENV):"
    echo "   Image: $CURRENT_IMAGE"
    echo "   Replicas: $CURRENT_REPLICAS"
else
    echo "❌ Current deployment $CURRENT_DEPLOYMENT not found"
    exit 1
fi

# 检查目标部署状态
if kubectl get deployment $TARGET_DEPLOYMENT -n $NAMESPACE &> /dev/null; then
    TARGET_IMAGE=$(kubectl get deployment $TARGET_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}')
    TARGET_REPLICAS=$(kubectl get deployment $TARGET_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.replicas}')
    echo "📊 Target Environment ($TARGET_ENV):"
    echo "   Image: $TARGET_IMAGE"
    echo "   Replicas: $TARGET_REPLICAS"
else
    echo "❌ Target deployment $TARGET_DEPLOYMENT not found"
    exit 1
fi

# 显示当前服务指向
CURRENT_SERVICE_ENV=$(kubectl get service $PRODUCTION_SERVICE -n $NAMESPACE -o jsonpath='{.spec.selector.environment}')
echo "📊 Current Production Service points to: $CURRENT_SERVICE_ENV"

# 第一步：部署到目标环境
echo ""
echo "🚀 Step 1: Deploying new version to $TARGET_ENV environment..."

# 更新目标环境的镜像
kubectl set image deployment/$TARGET_DEPLOYMENT -n $NAMESPACE api=$IMAGE_NAME:$NEW_VERSION

# 扩容目标环境
kubectl scale deployment $TARGET_DEPLOYMENT -n $NAMESPACE --replicas=$CURRENT_REPLICAS

# 等待目标环境部署完成
echo "⏳ Waiting for $TARGET_ENV deployment to be ready..."
if ! kubectl rollout status deployment/$TARGET_DEPLOYMENT -n $NAMESPACE --timeout=600s; then
    echo "❌ $TARGET_ENV deployment failed"
    
    # 清理失败的部署
    echo "🧹 Cleaning up failed deployment..."
    kubectl scale deployment $TARGET_DEPLOYMENT -n $NAMESPACE --replicas=0
    
    exit 1
fi

echo "✅ $TARGET_ENV environment deployed successfully"

# 第二步：健康检查
echo ""
echo "🏥 Step 2: Health check on $TARGET_ENV environment..."

# 等待Pod就绪
kubectl wait --for=condition=ready pod -l app=raft-storage,component=api,environment=$TARGET_ENV -n $NAMESPACE --timeout=300s

# 通过测试服务进行健康检查
echo "🔍 Performing health check..."
if kubectl run health-check-$TARGET_ENV --rm -i --tty --restart=Never --image=curlimages/curl -- \
    curl -f http://raft-api-service-$TARGET_ENV.$NAMESPACE.svc.cluster.local/api/v1/monitoring/health; then
    echo "✅ Health check passed for $TARGET_ENV environment"
else
    echo "❌ Health check failed for $TARGET_ENV environment"
    
    # 清理失败的部署
    echo "🧹 Cleaning up failed deployment..."
    kubectl scale deployment $TARGET_DEPLOYMENT -n $NAMESPACE --replicas=0
    
    exit 1
fi

# 第三步：测试确认
echo ""
echo "🧪 Step 3: Testing $TARGET_ENV environment..."
echo "Test URL: http://raft-api-test.local (points to $TARGET_ENV)"
echo ""
echo "You can now test the new version at:"
echo "  kubectl port-forward service/raft-api-service-test -n $NAMESPACE 8080:80"
echo "  curl http://localhost:8080/api/v1/monitoring/health"
echo ""

# 询问是否继续切换
read -p "🔄 Do you want to switch production traffic to $TARGET_ENV? (y/N): " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "🛑 Blue-green deployment paused. You can:"
    echo "  1. Continue testing the $TARGET_ENV environment"
    echo "  2. Run this script again to complete the switch"
    echo "  3. Scale down $TARGET_ENV: kubectl scale deployment $TARGET_DEPLOYMENT -n $NAMESPACE --replicas=0"
    exit 0
fi

# 第四步：切换生产流量
echo ""
echo "🔄 Step 4: Switching production traffic to $TARGET_ENV..."

# 更新生产服务的选择器
kubectl patch service $PRODUCTION_SERVICE -n $NAMESPACE -p '{"spec":{"selector":{"environment":"'$TARGET_ENV'"}}}'

# 验证切换
sleep 5
NEW_SERVICE_ENV=$(kubectl get service $PRODUCTION_SERVICE -n $NAMESPACE -o jsonpath='{.spec.selector.environment}')
if [ "$NEW_SERVICE_ENV" = "$TARGET_ENV" ]; then
    echo "✅ Production traffic switched to $TARGET_ENV successfully"
else
    echo "❌ Failed to switch production traffic"
    exit 1
fi

# 第五步：验证生产环境
echo ""
echo "🏥 Step 5: Verifying production environment..."

# 健康检查生产服务
echo "🔍 Checking production service health..."
if kubectl run health-check-production --rm -i --tty --restart=Never --image=curlimages/curl -- \
    curl -f http://$PRODUCTION_SERVICE.$NAMESPACE.svc.cluster.local/api/v1/monitoring/health; then
    echo "✅ Production health check passed"
else
    echo "❌ Production health check failed"
    
    # 自动回滚
    echo "🔙 Auto-rolling back to $CURRENT_ENV..."
    kubectl patch service $PRODUCTION_SERVICE -n $NAMESPACE -p '{"spec":{"selector":{"environment":"'$CURRENT_ENV'"}}}'
    
    echo "✅ Rolled back to $CURRENT_ENV"
    exit 1
fi

# 第六步：清理旧环境（可选）
echo ""
read -p "🧹 Do you want to scale down the old $CURRENT_ENV environment? (y/N): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🧹 Scaling down $CURRENT_ENV environment..."
    kubectl scale deployment $CURRENT_DEPLOYMENT -n $NAMESPACE --replicas=0
    echo "✅ $CURRENT_ENV environment scaled down"
else
    echo "ℹ️  $CURRENT_ENV environment kept running for quick rollback"
fi

# 显示最终状态
echo ""
echo "🎉 Blue-Green deployment completed successfully!"
echo ""
echo "📊 Final Status:"
echo "==============="
echo ""
echo "📋 Production Environment: $TARGET_ENV"
kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api,environment=$TARGET_ENV -o wide

echo ""
echo "📋 Standby Environment: $CURRENT_ENV"
kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api,environment=$CURRENT_ENV -o wide

echo ""
echo "🌐 Services:"
kubectl get services -n $NAMESPACE -l app=raft-storage,component=api

echo ""
echo "📝 Quick Rollback (if needed):"
echo "=============================="
echo ""
echo "To rollback to $CURRENT_ENV:"
echo "  # Scale up old environment"
echo "  kubectl scale deployment $CURRENT_DEPLOYMENT -n $NAMESPACE --replicas=$CURRENT_REPLICAS"
echo "  # Switch service back"
echo "  kubectl patch service $PRODUCTION_SERVICE -n $NAMESPACE -p '{\"spec\":{\"selector\":{\"environment\":\"$CURRENT_ENV\"}}}'"
echo ""
echo "📝 Access Information:"
echo "====================="
echo ""
echo "Production: http://raft-api.local"
echo "Test: http://raft-api-test.local"
echo ""
echo "Port forward:"
echo "  kubectl port-forward service/$PRODUCTION_SERVICE -n $NAMESPACE 8080:80" 