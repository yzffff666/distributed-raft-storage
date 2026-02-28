# Kubernetes 部署指南

## 概述

本目录包含分布式存储系统在Kubernetes环境中的完整部署配置。

## 架构组件

- **raft-storage** 命名空间：隔离所有相关资源
- **Redis**: 缓存和会话存储
- **Raft集群**: 3节点分布式一致性存储
- **API服务**: Spring Boot RESTful API (2副本)
- **监控**: Prometheus + Grafana
- **网络**: Ingress统一入口

## 目录结构

```
k8s/
├── namespaces/          # 命名空间配置
├── configmaps/          # 配置文件
├── secrets/             # 密钥配置
├── deployments/         # 部署配置
├── services/            # 服务配置
├── ingress/             # 入口配置
├── monitoring/          # 监控配置
└── README.md           # 本文件
```

## 快速部署

### 前置条件

1. Kubernetes集群 (v1.20+)
2. kubectl已配置
3. Ingress Controller (推荐nginx-ingress)
4. 存储类 (StorageClass: standard)

### 一键部署

```bash
# 从项目根目录执行
./deploy-k8s.sh
```

### 手动部署

```bash
# 1. 创建命名空间
kubectl apply -f namespaces/

# 2. 创建配置和密钥
kubectl apply -f configmaps/
kubectl apply -f secrets/

# 3. 创建存储
kubectl apply -f deployments/raft-nodes-pvc.yaml

# 4. 部署基础服务
kubectl apply -f deployments/redis-deployment.yaml
kubectl apply -f services/redis-service.yaml

# 5. 部署Raft集群
kubectl apply -f deployments/raft-nodes-deployment.yaml
kubectl apply -f services/raft-nodes-services.yaml

# 6. 部署API服务
kubectl apply -f deployments/api-deployment.yaml
kubectl apply -f services/api-service.yaml

# 7. 部署监控
kubectl apply -f monitoring/

# 8. 配置Ingress
kubectl apply -f ingress/
```

## 访问方式

### 通过Ingress (推荐)

在 `/etc/hosts` 中添加域名映射：
```
<INGRESS_IP> raft-api.local
<INGRESS_IP> prometheus.local
<INGRESS_IP> grafana.local
```

访问地址：
- API文档: http://raft-api.local/swagger-ui/index.html
- Prometheus: http://prometheus.local
- Grafana: http://grafana.local (admin/admin123)

### 通过NodePort

```bash
# 获取节点IP
kubectl get nodes -o wide

# 访问地址
API服务: http://<NODE_IP>:30080
Prometheus: http://<NODE_IP>:30090
Grafana: http://<NODE_IP>:30300
```

### 通过Port Forward

```bash
# API服务
kubectl port-forward svc/raft-api-service 8080:80 -n raft-storage

# Prometheus
kubectl port-forward svc/prometheus-service 9090:9090 -n raft-storage

# Grafana
kubectl port-forward svc/grafana-service 3000:3000 -n raft-storage
```

## 监控和运维

### 查看状态

```bash
# 查看所有资源
kubectl get all -n raft-storage

# 查看Pod状态
kubectl get pods -n raft-storage

# 查看服务状态
kubectl get svc -n raft-storage

# 查看存储
kubectl get pvc -n raft-storage
```

### 查看日志

```bash
# API服务日志
kubectl logs -f deployment/raft-api-deployment -n raft-storage

# Raft节点日志
kubectl logs -f deployment/raft-node-1-deployment -n raft-storage

# Redis日志
kubectl logs -f deployment/redis-deployment -n raft-storage
```

### 扩缩容

```bash
# 扩展API服务副本
kubectl scale deployment raft-api-deployment --replicas=3 -n raft-storage

# 查看扩容状态
kubectl get deployment raft-api-deployment -n raft-storage
```

## 故障排查

### 常见问题

1. **Pod一直Pending**
   - 检查资源配额: `kubectl describe pod <pod-name> -n raft-storage`
   - 检查存储类: `kubectl get storageclass`

2. **服务无法访问**
   - 检查Service: `kubectl get svc -n raft-storage`
   - 检查Endpoints: `kubectl get endpoints -n raft-storage`

3. **Ingress不工作**
   - 检查Ingress Controller: `kubectl get pods -n ingress-nginx`
   - 检查Ingress规则: `kubectl describe ingress raft-ingress -n raft-storage`

### 调试命令

```bash
# 进入Pod调试
kubectl exec -it <pod-name> -n raft-storage -- /bin/sh

# 查看事件
kubectl get events -n raft-storage --sort-by=.metadata.creationTimestamp

# 查看资源使用
kubectl top pods -n raft-storage
```

## 清理部署

```bash
# 一键清理
./cleanup-k8s.sh

# 或手动删除
kubectl delete namespace raft-storage
```

## 高可用配置

### 生产环境建议

1. **多副本部署**
   - API服务: 3副本
   - 监控服务: 2副本

2. **资源限制**
   - 设置合适的requests和limits
   - 配置HPA自动扩缩容

3. **存储优化**
   - 使用高性能存储类
   - 配置备份策略

4. **网络安全**
   - 配置NetworkPolicy
   - 启用TLS加密

5. **监控告警**
   - 配置Prometheus告警规则
   - 集成告警通知
```
