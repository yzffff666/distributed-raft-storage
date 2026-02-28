# Raft Storage Helm Chart

这是一个用于部署Raft分布式存储系统的Helm Chart，提供了完整的云原生部署解决方案。

## 功能特性

- 🚀 **一键部署**: 使用Helm一键部署整个Raft存储集群
- 🔄 **自动扩缩容**: 支持HPA自动水平扩缩容
- 📊 **监控告警**: 集成Prometheus和Grafana监控
- 🔒 **安全认证**: JWT认证和RBAC权限控制
- 💾 **持久化存储**: 支持多种存储类型和PVC管理
- 🌐 **服务发现**: 完整的Service和Ingress配置
- 🛡️ **高可用**: 支持多副本部署和Pod中断预算

## 目录结构

```
helm/
├── raft-storage/           # Helm Chart目录
│   ├── Chart.yaml         # Chart元数据
│   ├── values.yaml        # 默认配置值
│   └── templates/         # Kubernetes模板文件
│       ├── _helpers.tpl   # 辅助模板
│       ├── namespace.yaml # 命名空间
│       ├── configmap.yaml # 配置映射
│       ├── secret.yaml    # 密钥
│       ├── serviceaccount.yaml # 服务账户
│       ├── api-deployment.yaml # API服务部署
│       ├── raft-statefulset.yaml # Raft节点状态集
│       ├── service.yaml   # 服务
│       ├── ingress.yaml   # 入口
│       ├── pvc.yaml       # 持久卷声明
│       ├── hpa.yaml       # 水平扩缩容
│       ├── pdb.yaml       # Pod中断预算
│       └── NOTES.txt      # 部署说明
├── deploy-helm.sh         # 部署脚本
├── cleanup-helm.sh        # 清理脚本
└── README.md             # 使用文档
```

## 前置条件

1. **Kubernetes集群**: 版本 >= 1.19
2. **Helm**: 版本 >= 3.0
3. **kubectl**: 已配置并能访问集群
4. **存储类**: 集群中有可用的StorageClass

### 安装Helm

```bash
# macOS
brew install helm

# Linux
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Windows
choco install kubernetes-helm
```

## 快速开始

### 1. 部署应用

```bash
# 进入helm目录
cd helm

# 执行部署脚本
./deploy-helm.sh
```

### 2. 手动部署

```bash
# 添加依赖仓库
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# 部署应用
helm upgrade --install raft-storage ./raft-storage \
    --namespace raft-storage \
    --create-namespace \
    --wait
```

### 3. 访问应用

```bash
# API服务端口转发
kubectl port-forward --namespace raft-storage svc/raft-storage-api 8080:80

# 访问API文档
open http://localhost:8080/api/v1/swagger-ui/index.html

# 监控服务端口转发
kubectl port-forward --namespace raft-storage svc/raft-storage-prometheus-server 9090:80
kubectl port-forward --namespace raft-storage svc/raft-storage-grafana 3000:80
```

## 配置说明

### 主要配置项

```yaml
# API服务配置
api:
  enabled: true
  replicaCount: 2
  image:
    repository: raft-api
    tag: latest
  
  # 资源限制
  resources:
    requests:
      memory: "512Mi"
      cpu: "300m"
    limits:
      memory: "1Gi"
      cpu: "1000m"

# Raft集群配置
raft:
  enabled: true
  nodes:
    - id: 1
      port: 8051
    - id: 2
      port: 8052
    - id: 3
      port: 8053

# Redis配置
redis:
  enabled: true
  auth:
    enabled: false

# 监控配置
monitoring:
  enabled: true
  prometheus:
    enabled: true
  grafana:
    enabled: true

# 自动扩缩容
autoscaling:
  enabled: false
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 80
```

### 自定义配置

创建自定义values文件：

```yaml
# custom-values.yaml
api:
  replicaCount: 3
  resources:
    requests:
      memory: "1Gi"
      cpu: "500m"
    limits:
      memory: "2Gi"
      cpu: "1500m"

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 20

monitoring:
  prometheus:
    server:
      persistentVolume:
        size: 50Gi
```

使用自定义配置部署：

```bash
helm upgrade --install raft-storage ./raft-storage \
    --namespace raft-storage \
    --create-namespace \
    --values custom-values.yaml
```

## 运维操作

### 查看部署状态

```bash
# 查看Helm发布状态
helm status raft-storage --namespace raft-storage

# 查看Pod状态
kubectl get pods --namespace raft-storage

# 查看服务状态
kubectl get services --namespace raft-storage

# 查看存储状态
kubectl get pvc --namespace raft-storage
```

### 升级应用

```bash
# 升级到新版本
helm upgrade raft-storage ./raft-storage \
    --namespace raft-storage \
    --set api.image.tag=v2.0.0

# 回滚到上一版本
helm rollback raft-storage --namespace raft-storage
```

### 扩缩容操作

```bash
# 手动扩容API服务
kubectl scale deployment raft-storage-api \
    --replicas=5 \
    --namespace raft-storage

# 启用自动扩缩容
helm upgrade raft-storage ./raft-storage \
    --namespace raft-storage \
    --set autoscaling.enabled=true
```

### 备份和恢复

```bash
# 备份配置
helm get values raft-storage --namespace raft-storage > backup-values.yaml

# 备份数据（需要根据存储类型调整）
kubectl exec -n raft-storage raft-storage-raft-node-1-0 -- \
    tar czf /tmp/backup.tar.gz /app/data

# 恢复数据
kubectl cp backup.tar.gz raft-storage/raft-storage-raft-node-1-0:/tmp/
kubectl exec -n raft-storage raft-storage-raft-node-1-0 -- \
    tar xzf /tmp/backup.tar.gz -C /
```

## 卸载应用

### 使用脚本卸载

```bash
# 执行清理脚本
./cleanup-helm.sh
```

### 手动卸载

```bash
# 卸载Helm发布
helm uninstall raft-storage --namespace raft-storage

# 删除命名空间（可选）
kubectl delete namespace raft-storage

# 删除PVC（会丢失数据）
kubectl delete pvc --all --namespace raft-storage
```

## 故障排查

### 常见问题

1. **Pod无法启动**
   ```bash
   # 查看Pod事件
   kubectl describe pod <pod-name> --namespace raft-storage
   
   # 查看Pod日志
   kubectl logs <pod-name> --namespace raft-storage
   ```

2. **存储问题**
   ```bash
   # 查看PVC状态
   kubectl get pvc --namespace raft-storage
   
   # 查看存储类
   kubectl get storageclass
   ```

3. **网络问题**
   ```bash
   # 查看服务端点
   kubectl get endpoints --namespace raft-storage
   
   # 测试服务连通性
   kubectl run test-pod --image=busybox --rm -it --restart=Never -- \
     wget -qO- http://raft-storage-api.raft-storage.svc.cluster.local/api/v1/monitoring/health
   ```

4. **配置问题**
   ```bash
   # 查看ConfigMap
   kubectl get configmap --namespace raft-storage
   
   # 查看Secret
   kubectl get secret --namespace raft-storage
   ```

### 日志收集

```bash
# 收集所有Pod日志
for pod in $(kubectl get pods --namespace raft-storage -o name); do
    echo "=== $pod ==="
    kubectl logs $pod --namespace raft-storage
done > raft-storage-logs.txt
```

## 性能调优

### 资源配置建议

| 组件 | CPU请求 | 内存请求 | CPU限制 | 内存限制 |
|------|---------|----------|---------|----------|
| API服务 | 300m | 512Mi | 1000m | 1Gi |
| Raft节点 | 200m | 256Mi | 500m | 512Mi |
| Redis | 100m | 128Mi | 500m | 512Mi |
| Prometheus | 200m | 512Mi | 500m | 1Gi |
| Grafana | 100m | 256Mi | 300m | 512Mi |

### 存储配置建议

| 组件 | 存储类型 | 大小建议 | 访问模式 |
|------|----------|----------|----------|
| Raft数据 | SSD | 10-100Gi | ReadWriteOnce |
| Raft日志 | SSD | 5-50Gi | ReadWriteOnce |
| API日志 | HDD | 5-20Gi | ReadWriteMany |
| Prometheus | SSD | 20-200Gi | ReadWriteOnce |
| Grafana | HDD | 5-20Gi | ReadWriteOnce |

## 安全配置

### 网络策略

```yaml
# 启用网络策略
networkPolicy:
  enabled: true
```

### RBAC配置

```yaml
# 自定义服务账户
serviceAccount:
  create: true
  name: "raft-storage-sa"
  annotations:
    kubernetes.io/service-account.name: raft-storage-sa
```

### 安全上下文

```yaml
# Pod安全上下文
securityContext:
  runAsNonRoot: true
  runAsUser: 1001
  fsGroup: 1001
```

## 贡献指南

1. Fork本项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 许可证

本项目采用MIT许可证 - 查看 [LICENSE](../LICENSE) 文件了解详情。

## 支持

- 📧 邮箱: 1367016356@qq.com
- 🐛 问题反馈: [GitHub Issues](https://github.com/yzffff666/distributed-raft-storage/issues)
- 📖 文档: [项目文档](../doc/)
