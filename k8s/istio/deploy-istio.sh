#!/bin/bash

# Istio服务网格部署脚本
set -e

ISTIO_VERSION="${1:-1.20.1}"
NAMESPACE="raft-storage"
ISTIO_NAMESPACE="istio-system"

echo "=== Raft Storage Istio Service Mesh Deployment Script ==="
echo "Istio Version: $ISTIO_VERSION"
echo "Application Namespace: $NAMESPACE"
echo "Istio Namespace: $ISTIO_NAMESPACE"

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

# 检查Istio是否已安装
if kubectl get namespace $ISTIO_NAMESPACE &> /dev/null; then
    echo "📊 Istio namespace already exists"
    
    # 检查Istio版本
    if kubectl get deployment istiod -n $ISTIO_NAMESPACE &> /dev/null; then
        CURRENT_VERSION=$(kubectl get deployment istiod -n $ISTIO_NAMESPACE -o jsonpath='{.metadata.labels.version}' || echo "unknown")
        echo "📊 Current Istio version: $CURRENT_VERSION"
        
        if [ "$CURRENT_VERSION" != "$ISTIO_VERSION" ]; then
            echo "⚠️  Version mismatch detected"
            read -p "🔄 Do you want to upgrade Istio to $ISTIO_VERSION? (y/N): " -n 1 -r
            echo
            
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                echo "ℹ️  Using existing Istio installation"
            else
                echo "🚀 Upgrading Istio..."
                # 这里可以添加Istio升级逻辑
            fi
        else
            echo "✅ Istio version matches requirements"
        fi
    else
        echo "⚠️  Istio namespace exists but istiod not found"
    fi
else
    echo "📥 Installing Istio $ISTIO_VERSION..."
    
    # 下载Istio
    if [ ! -d "istio-$ISTIO_VERSION" ]; then
        echo "📥 Downloading Istio $ISTIO_VERSION..."
        curl -L https://istio.io/downloadIstio | ISTIO_VERSION=$ISTIO_VERSION sh -
    fi
    
    # 添加istioctl到PATH
    export PATH=$PWD/istio-$ISTIO_VERSION/bin:$PATH
    
    # 检查istioctl
    if ! command -v istioctl &> /dev/null; then
        echo "❌ istioctl not found in PATH"
        exit 1
    fi
    
    # 预检查
    echo "🔍 Running Istio pre-check..."
    istioctl x precheck
    
    # 安装Istio
    echo "🚀 Installing Istio with demo profile..."
    istioctl install --set values.defaultRevision=default -y
    
    echo "✅ Istio installed successfully"
fi

# 检查应用命名空间是否存在
if ! kubectl get namespace $NAMESPACE &> /dev/null; then
    echo "❌ Application namespace $NAMESPACE does not exist. Please deploy the application first."
    exit 1
fi

# 启用Istio sidecar注入
echo "💉 Enabling Istio sidecar injection for namespace $NAMESPACE..."
kubectl label namespace $NAMESPACE istio-injection=enabled --overwrite

# 等待Istio组件就绪
echo "⏳ Waiting for Istio components to be ready..."
kubectl wait --for=condition=available deployment/istiod -n $ISTIO_NAMESPACE --timeout=300s
kubectl wait --for=condition=available deployment/istio-ingressgateway -n $ISTIO_NAMESPACE --timeout=300s

# 部署Istio配置
echo "🔧 Deploying Istio configurations..."

# 创建TLS证书（自签名用于测试）
echo "🔐 Creating TLS certificate..."
if ! kubectl get secret raft-storage-tls-secret -n $ISTIO_NAMESPACE &> /dev/null; then
    # 生成自签名证书
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout /tmp/tls.key -out /tmp/tls.crt \
        -subj "/CN=raft-api.local/O=raft-storage" \
        -addext "subjectAltName=DNS:raft-api.local,DNS:raft-admin.local,DNS:raft-monitoring.local"
    
    # 创建Secret
    kubectl create secret tls raft-storage-tls-secret \
        --key=/tmp/tls.key --cert=/tmp/tls.crt -n $ISTIO_NAMESPACE
    
    # 清理临时文件
    rm -f /tmp/tls.key /tmp/tls.crt
    
    echo "✅ TLS certificate created"
else
    echo "ℹ️  TLS certificate already exists"
fi

# 部署Gateway和VirtualService
echo "🌐 Deploying Gateway and VirtualService..."
kubectl apply -f gateway.yaml

# 部署DestinationRules
echo "🎯 Deploying DestinationRules..."
kubectl apply -f destination-rules.yaml

# 部署安全策略
echo "🔒 Deploying security policies..."
kubectl apply -f security-policies.yaml

# 部署遥测配置
echo "📊 Deploying telemetry configurations..."
kubectl apply -f telemetry.yaml

# 等待配置生效
echo "⏳ Waiting for configurations to take effect..."
sleep 10

# 验证部署
echo ""
echo "🔍 Verifying Istio deployment..."
echo "================================"

# 检查Istio组件状态
echo ""
echo "📋 Istio Components Status:"
kubectl get pods -n $ISTIO_NAMESPACE

# 检查应用Pod的sidecar注入
echo ""
echo "📋 Application Pods (with sidecar):"
kubectl get pods -n $NAMESPACE -o wide

# 检查Gateway状态
echo ""
echo "🌐 Gateway Status:"
kubectl get gateway -n $NAMESPACE

# 检查VirtualService状态
echo ""
echo "🎯 VirtualService Status:"
kubectl get virtualservice -n $NAMESPACE

# 检查DestinationRule状态
echo ""
echo "📊 DestinationRule Status:"
kubectl get destinationrule -n $NAMESPACE

# 获取Ingress Gateway外部IP
echo ""
echo "🌍 Getting Ingress Gateway external access..."
INGRESS_HOST=$(kubectl get service istio-ingressgateway -n $ISTIO_NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
INGRESS_PORT=$(kubectl get service istio-ingressgateway -n $ISTIO_NAMESPACE -o jsonpath='{.spec.ports[?(@.name=="http2")].port}')
SECURE_INGRESS_PORT=$(kubectl get service istio-ingressgateway -n $ISTIO_NAMESPACE -o jsonpath='{.spec.ports[?(@.name=="https")].port}')

if [ -z "$INGRESS_HOST" ]; then
    INGRESS_HOST=$(kubectl get service istio-ingressgateway -n $ISTIO_NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
fi

if [ -z "$INGRESS_HOST" ]; then
    echo "⚠️  External LoadBalancer not available. Using NodePort access..."
    INGRESS_HOST=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}')
    if [ -z "$INGRESS_HOST" ]; then
        INGRESS_HOST=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
    fi
    INGRESS_PORT=$(kubectl get service istio-ingressgateway -n $ISTIO_NAMESPACE -o jsonpath='{.spec.ports[?(@.name=="http2")].nodePort}')
    SECURE_INGRESS_PORT=$(kubectl get service istio-ingressgateway -n $ISTIO_NAMESPACE -o jsonpath='{.spec.ports[?(@.name=="https")].nodePort}')
fi

# 健康检查
echo ""
echo "🏥 Health Check:"
echo "==============="

if [ -n "$INGRESS_HOST" ]; then
    echo "🔍 Testing API health through Istio Gateway..."
    
    # 添加hosts条目提示
    echo ""
    echo "📝 Add the following entries to your /etc/hosts file:"
    echo "$INGRESS_HOST raft-api.local"
    echo "$INGRESS_HOST raft-admin.local"
    echo "$INGRESS_HOST raft-monitoring.local"
    echo ""
    
    # 测试健康检查
    echo "🧪 Testing health check (you may need to add hosts entries first):"
    echo "curl -k https://raft-api.local:$SECURE_INGRESS_PORT/api/v1/monitoring/health"
    echo ""
    
    # 尝试直接测试（如果可能）
    if command -v curl &> /dev/null; then
        echo "🔬 Attempting direct health check..."
        if curl -k -H "Host: raft-api.local" "https://$INGRESS_HOST:$SECURE_INGRESS_PORT/api/v1/monitoring/health" --connect-timeout 10 --max-time 30; then
            echo "✅ Health check passed"
        else
            echo "⚠️  Health check failed (this might be normal if hosts are not configured)"
        fi
    fi
else
    echo "⚠️  Could not determine ingress host"
fi

# 显示访问信息
echo ""
echo "🎉 Istio Service Mesh deployment completed!"
echo ""
echo "📝 Access Information:"
echo "====================="
echo ""
echo "🌐 External Access URLs:"
echo "  API Service: https://raft-api.local:$SECURE_INGRESS_PORT"
echo "  Admin Interface: https://raft-admin.local:$SECURE_INGRESS_PORT"
echo "  Monitoring: https://raft-monitoring.local:$SECURE_INGRESS_PORT"
echo ""
echo "🔧 Port Forward Access:"
echo "  kubectl port-forward service/istio-ingressgateway -n $ISTIO_NAMESPACE 8080:80 8443:443"
echo ""
echo "📊 Istio Dashboard:"
echo "  kubectl port-forward service/kiali -n $ISTIO_NAMESPACE 20001:20001"
echo "  kubectl port-forward service/jaeger -n $ISTIO_NAMESPACE 16686:16686"
echo "  kubectl port-forward service/grafana -n $ISTIO_NAMESPACE 3000:3000"
echo ""
echo "🔍 Useful Commands:"
echo "=================="
echo ""
echo "Check Istio proxy status:"
echo "  istioctl proxy-status"
echo ""
echo "Check Istio configuration:"
echo "  istioctl analyze -n $NAMESPACE"
echo ""
echo "View Envoy configuration:"
echo "  istioctl proxy-config cluster <pod-name> -n $NAMESPACE"
echo ""
echo "Check mTLS status:"
echo "  istioctl authn tls-check <pod-name>.<namespace>.svc.cluster.local"
echo ""
echo "Monitor traffic:"
echo "  istioctl dashboard kiali" 