#!/bin/bash

# 自动扩缩容部署脚本
set -e

NAMESPACE="raft-storage"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Raft Storage Auto-scaling Deployment Script ==="

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

# 检查命名空间是否存在
if ! kubectl get namespace $NAMESPACE &> /dev/null; then
    echo "❌ Namespace $NAMESPACE does not exist. Please deploy the main application first."
    exit 1
fi

echo "✅ Namespace $NAMESPACE exists"

# 检查Metrics Server是否部署
echo "🔍 Checking Metrics Server..."
if ! kubectl get deployment metrics-server -n kube-system &> /dev/null; then
    echo "⚠️  Metrics Server not found. Deploying Metrics Server..."
    
    # 部署Metrics Server
    kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
    
    # 等待Metrics Server就绪
    echo "⏳ Waiting for Metrics Server to be ready..."
    kubectl wait --for=condition=ready pod -l k8s-app=metrics-server -n kube-system --timeout=300s
    
    echo "✅ Metrics Server deployed successfully"
else
    echo "✅ Metrics Server already exists"
fi

# 检查Prometheus Operator是否部署（用于自定义指标）
echo "🔍 Checking Prometheus Operator..."
if ! kubectl get crd prometheusrules.monitoring.coreos.com &> /dev/null; then
    echo "⚠️  Prometheus Operator not found. Installing Prometheus Operator..."
    
    # 安装Prometheus Operator
    kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
    
    # 使用kube-prometheus-stack Helm chart
    if command -v helm &> /dev/null; then
        helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
        helm repo update
        
        helm upgrade --install prometheus-operator prometheus-community/kube-prometheus-stack \
            --namespace monitoring \
            --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
            --set prometheus.prometheusSpec.ruleSelectorNilUsesHelmValues=false \
            --wait
        
        echo "✅ Prometheus Operator installed via Helm"
    else
        echo "⚠️  Helm not found. Please install Prometheus Operator manually or install Helm."
        echo "   You can continue without custom metrics, but only CPU/Memory scaling will work."
    fi
else
    echo "✅ Prometheus Operator already exists"
fi

# 检查VPA是否部署（可选）
echo "🔍 Checking Vertical Pod Autoscaler..."
if ! kubectl get crd verticalpodautoscalers.autoscaling.k8s.io &> /dev/null; then
    echo "⚠️  VPA not found. You can install it manually if needed:"
    echo "   git clone https://github.com/kubernetes/autoscaler.git"
    echo "   cd autoscaler/vertical-pod-autoscaler/"
    echo "   ./hack/vpa-install.sh"
    echo "   For now, continuing without VPA..."
else
    echo "✅ VPA already exists"
fi

# 部署自定义指标配置
echo "🚀 Deploying custom metrics configuration..."
kubectl apply -f $SCRIPT_DIR/hpa/custom-metrics-config.yaml

# 部署扩缩容策略配置
echo "🚀 Deploying scaling policies..."
kubectl apply -f $SCRIPT_DIR/hpa/scaling-policies.yaml

# 部署HPA配置
echo "🚀 Deploying HPA configurations..."
kubectl apply -f $SCRIPT_DIR/hpa/api-hpa.yaml

# 部署VPA配置（如果VPA可用）
if kubectl get crd verticalpodautoscalers.autoscaling.k8s.io &> /dev/null; then
    echo "🚀 Deploying VPA configurations..."
    kubectl apply -f $SCRIPT_DIR/hpa/vpa.yaml
fi

# 等待HPA就绪
echo "⏳ Waiting for HPA to be ready..."
sleep 10

# 检查HPA状态
echo "📊 Checking HPA status..."
kubectl get hpa -n $NAMESPACE

# 检查自定义指标是否可用
echo "🔍 Checking custom metrics availability..."
if kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1" &> /dev/null; then
    echo "✅ Custom metrics API available"
    
    # 列出可用的自定义指标
    echo "📊 Available custom metrics:"
    kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1" | jq '.resources[].name' 2>/dev/null || echo "   (jq not available for detailed listing)"
else
    echo "⚠️  Custom metrics API not available. Only CPU/Memory scaling will work."
fi

# 显示部署状态
echo ""
echo "📊 Auto-scaling Deployment Status:"
echo "=================================="

echo ""
echo "🔄 HPA Status:"
kubectl get hpa -n $NAMESPACE -o wide

if kubectl get crd verticalpodautoscalers.autoscaling.k8s.io &> /dev/null; then
    echo ""
    echo "📈 VPA Status:"
    kubectl get vpa -n $NAMESPACE -o wide
fi

echo ""
echo "📋 ConfigMaps:"
kubectl get configmap -n $NAMESPACE | grep -E "(scaling|metrics)"

echo ""
echo "⏰ CronJobs:"
kubectl get cronjob -n $NAMESPACE

echo ""
echo "🎯 Monitoring Resources:"
kubectl get servicemonitor,prometheusrule -n $NAMESPACE 2>/dev/null || echo "   ServiceMonitor/PrometheusRule not available (normal if Prometheus Operator not installed)"

# 提供使用说明
echo ""
echo "🎉 Auto-scaling deployment completed!"
echo ""
echo "📝 Usage Instructions:"
echo "====================="
echo ""
echo "1. Monitor HPA status:"
echo "   kubectl get hpa -n $NAMESPACE -w"
echo ""
echo "2. Check scaling events:"
echo "   kubectl describe hpa raft-api-hpa -n $NAMESPACE"
echo ""
echo "3. Monitor pod scaling:"
echo "   kubectl get pods -n $NAMESPACE -w"
echo ""
echo "4. View scaling policies:"
echo "   kubectl get configmap scaling-policies -n $NAMESPACE -o yaml"
echo ""
echo "5. Test scaling manually:"
echo "   # Generate load to trigger scaling"
echo "   kubectl run -i --tty load-generator --rm --image=busybox --restart=Never -- /bin/sh"
echo "   # Inside the container:"
echo "   # while true; do wget -q -O- http://raft-api-service.raft-storage.svc.cluster.local/api/v1/monitoring/health; done"
echo ""
echo "6. Check custom metrics (if available):"
echo "   kubectl get --raw '/apis/custom.metrics.k8s.io/v1beta1/namespaces/$NAMESPACE/pods/*/http_requests_per_second'"
echo ""
echo "7. View VPA recommendations (if VPA is installed):"
echo "   kubectl describe vpa -n $NAMESPACE"
echo ""
echo "📚 For more information, check the documentation in k8s/hpa/" 