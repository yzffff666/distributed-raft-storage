#!/bin/bash

# 金丝雀部署脚本
set -e

NAMESPACE="raft-storage"
NEW_VERSION="${1:-v1.10.0}"
IMAGE_NAME="${2:-raft-api}"
CANARY_WEIGHT="${3:-10}"  # 金丝雀流量百分比

echo "=== Raft Storage Canary Deployment Script ==="
echo "Namespace: $NAMESPACE"
echo "New Version: $NEW_VERSION"
echo "Image: $IMAGE_NAME:$NEW_VERSION"
echo "Canary Weight: $CANARY_WEIGHT%"

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

# 部署名称
STABLE_DEPLOYMENT="raft-api-stable"
CANARY_DEPLOYMENT="raft-api-canary"

# 检查稳定版部署
if kubectl get deployment $STABLE_DEPLOYMENT -n $NAMESPACE &> /dev/null; then
    STABLE_IMAGE=$(kubectl get deployment $STABLE_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}')
    STABLE_REPLICAS=$(kubectl get deployment $STABLE_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.replicas}')
    echo "📊 Stable Version:"
    echo "   Image: $STABLE_IMAGE"
    echo "   Replicas: $STABLE_REPLICAS"
else
    echo "❌ Stable deployment $STABLE_DEPLOYMENT not found"
    exit 1
fi

# 检查金丝雀部署
if kubectl get deployment $CANARY_DEPLOYMENT -n $NAMESPACE &> /dev/null; then
    CANARY_IMAGE=$(kubectl get deployment $CANARY_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.template.spec.containers[0].image}')
    CANARY_REPLICAS=$(kubectl get deployment $CANARY_DEPLOYMENT -n $NAMESPACE -o jsonpath='{.spec.replicas}')
    echo "📊 Current Canary:"
    echo "   Image: $CANARY_IMAGE"
    echo "   Replicas: $CANARY_REPLICAS"
else
    echo "❌ Canary deployment $CANARY_DEPLOYMENT not found"
    exit 1
fi

# 计算副本数
TOTAL_REPLICAS=$((STABLE_REPLICAS + CANARY_REPLICAS))
NEW_CANARY_REPLICAS=$(((TOTAL_REPLICAS * CANARY_WEIGHT) / 100))
NEW_STABLE_REPLICAS=$((TOTAL_REPLICAS - NEW_CANARY_REPLICAS))

# 确保至少有1个副本
if [ $NEW_CANARY_REPLICAS -eq 0 ]; then
    NEW_CANARY_REPLICAS=1
    NEW_STABLE_REPLICAS=$((TOTAL_REPLICAS - 1))
fi

echo ""
echo "📊 Planned Replica Distribution:"
echo "   Stable: $NEW_STABLE_REPLICAS replicas ($((100 - CANARY_WEIGHT))%)"
echo "   Canary: $NEW_CANARY_REPLICAS replicas ($CANARY_WEIGHT%)"

# 确认是否继续
echo ""
read -p "🚀 Do you want to proceed with canary deployment? (y/N): " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Canary deployment cancelled by user"
    exit 0
fi

# 第一步：更新金丝雀版本
echo ""
echo "🚀 Step 1: Updating canary deployment to $NEW_VERSION..."

kubectl set image deployment/$CANARY_DEPLOYMENT -n $NAMESPACE api=$IMAGE_NAME:$NEW_VERSION

# 等待金丝雀部署完成
echo "⏳ Waiting for canary deployment to be ready..."
if ! kubectl rollout status deployment/$CANARY_DEPLOYMENT -n $NAMESPACE --timeout=300s; then
    echo "❌ Canary deployment failed"
    exit 1
fi

echo "✅ Canary deployment updated successfully"

# 第二步：调整副本数
echo ""
echo "🔄 Step 2: Adjusting replica distribution..."

# 同时调整两个部署的副本数
kubectl scale deployment $STABLE_DEPLOYMENT -n $NAMESPACE --replicas=$NEW_STABLE_REPLICAS &
kubectl scale deployment $CANARY_DEPLOYMENT -n $NAMESPACE --replicas=$NEW_CANARY_REPLICAS &

# 等待两个扩缩容操作完成
wait

# 等待Pod就绪
echo "⏳ Waiting for pods to be ready..."
kubectl wait --for=condition=ready pod -l app=raft-storage,component=api,track=stable -n $NAMESPACE --timeout=300s
kubectl wait --for=condition=ready pod -l app=raft-storage,component=api,track=canary -n $NAMESPACE --timeout=300s

echo "✅ Replica distribution updated successfully"

# 第三步：健康检查
echo ""
echo "🏥 Step 3: Health check..."

# 检查金丝雀版本健康状态
echo "🔍 Checking canary health..."
if kubectl run health-check-canary --rm -i --tty --restart=Never --image=curlimages/curl -- \
    curl -f http://raft-api-service-canary.$NAMESPACE.svc.cluster.local/api/v1/monitoring/health; then
    echo "✅ Canary health check passed"
else
    echo "❌ Canary health check failed"
    
    # 回滚金丝雀
    echo "🔙 Rolling back canary..."
    kubectl scale deployment $CANARY_DEPLOYMENT -n $NAMESPACE --replicas=0
    kubectl scale deployment $STABLE_DEPLOYMENT -n $NAMESPACE --replicas=$TOTAL_REPLICAS
    
    exit 1
fi

# 第四步：监控阶段
echo ""
echo "📊 Step 4: Monitoring canary deployment..."
echo ""
echo "Current traffic distribution:"
echo "  Stable ($((100 - CANARY_WEIGHT))%): $NEW_STABLE_REPLICAS pods"
echo "  Canary ($CANARY_WEIGHT%): $NEW_CANARY_REPLICAS pods"
echo ""

# 显示当前Pod状态
echo "📋 Current Pod Status:"
echo "Stable pods:"
kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api,track=stable -o wide

echo ""
echo "Canary pods:"
kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api,track=canary -o wide

# 监控建议
echo ""
echo "📈 Monitoring Recommendations:"
echo "=============================="
echo ""
echo "1. Monitor application metrics:"
echo "   kubectl port-forward service/prometheus -n monitoring 9090:9090"
echo "   # Check error rates, response times, etc."
echo ""
echo "2. Check application logs:"
echo "   kubectl logs -f deployment/$CANARY_DEPLOYMENT -n $NAMESPACE"
echo "   kubectl logs -f deployment/$STABLE_DEPLOYMENT -n $NAMESPACE"
echo ""
echo "3. Test canary version specifically:"
echo "   kubectl run test-canary --rm -i --tty --restart=Never --image=curlimages/curl -- \\"
echo "     curl -H 'canary: true' http://raft-api-service.$NAMESPACE.svc.cluster.local/api/v1/monitoring/health"
echo ""

# 询问下一步操作
echo ""
echo "🤔 What would you like to do next?"
echo "1. Promote canary to stable (100% traffic)"
echo "2. Increase canary traffic percentage"
echo "3. Rollback canary deployment"
echo "4. Keep current distribution"
echo ""

read -p "Choose an option (1-4): " -n 1 -r
echo

case $REPLY in
    1)
        echo ""
        echo "🚀 Promoting canary to stable..."
        
        # 将金丝雀版本推广到稳定版本
        kubectl set image deployment/$STABLE_DEPLOYMENT -n $NAMESPACE api=$IMAGE_NAME:$NEW_VERSION
        kubectl scale deployment $STABLE_DEPLOYMENT -n $NAMESPACE --replicas=$TOTAL_REPLICAS
        kubectl scale deployment $CANARY_DEPLOYMENT -n $NAMESPACE --replicas=0
        
        echo "⏳ Waiting for stable deployment to be ready..."
        kubectl rollout status deployment/$STABLE_DEPLOYMENT -n $NAMESPACE --timeout=300s
        
        echo "✅ Canary promoted to stable successfully!"
        echo "📊 All traffic is now on version $NEW_VERSION"
        ;;
    2)
        echo ""
        read -p "Enter new canary traffic percentage (1-100): " NEW_WEIGHT
        
        if [[ $NEW_WEIGHT =~ ^[0-9]+$ ]] && [ $NEW_WEIGHT -ge 1 ] && [ $NEW_WEIGHT -le 100 ]; then
            NEW_CANARY_REPLICAS=$(((TOTAL_REPLICAS * NEW_WEIGHT) / 100))
            NEW_STABLE_REPLICAS=$((TOTAL_REPLICAS - NEW_CANARY_REPLICAS))
            
            if [ $NEW_CANARY_REPLICAS -eq 0 ]; then
                NEW_CANARY_REPLICAS=1
                NEW_STABLE_REPLICAS=$((TOTAL_REPLICAS - 1))
            fi
            
            echo "🔄 Adjusting traffic to $NEW_WEIGHT% canary..."
            kubectl scale deployment $STABLE_DEPLOYMENT -n $NAMESPACE --replicas=$NEW_STABLE_REPLICAS
            kubectl scale deployment $CANARY_DEPLOYMENT -n $NAMESPACE --replicas=$NEW_CANARY_REPLICAS
            
            echo "✅ Traffic distribution updated"
        else
            echo "❌ Invalid percentage. Keeping current distribution."
        fi
        ;;
    3)
        echo ""
        echo "🔙 Rolling back canary deployment..."
        
        kubectl scale deployment $CANARY_DEPLOYMENT -n $NAMESPACE --replicas=0
        kubectl scale deployment $STABLE_DEPLOYMENT -n $NAMESPACE --replicas=$TOTAL_REPLICAS
        
        echo "✅ Canary rollback completed"
        echo "📊 All traffic is back on stable version"
        ;;
    4)
        echo ""
        echo "ℹ️  Keeping current distribution"
        echo "📊 Monitor the deployment and run this script again when ready"
        ;;
    *)
        echo ""
        echo "❌ Invalid option. Keeping current distribution."
        ;;
esac

# 显示最终状态
echo ""
echo "📊 Final Status:"
echo "==============="
echo ""
kubectl get pods -n $NAMESPACE -l app=raft-storage,component=api -o wide

echo ""
echo "🌐 Services:"
kubectl get services -n $NAMESPACE -l app=raft-storage,component=api

echo ""
echo "📝 Useful Commands:"
echo "=================="
echo ""
echo "Monitor pods:"
echo "  kubectl get pods -n $NAMESPACE -w"
echo ""
echo "Check logs:"
echo "  kubectl logs -f deployment/$STABLE_DEPLOYMENT -n $NAMESPACE"
echo "  kubectl logs -f deployment/$CANARY_DEPLOYMENT -n $NAMESPACE"
echo ""
echo "Test specific version:"
echo "  # Test stable version"
echo "  kubectl port-forward service/raft-api-service-stable -n $NAMESPACE 8080:80"
echo "  # Test canary version"
echo "  kubectl port-forward service/raft-api-service-canary -n $NAMESPACE 8081:80" 