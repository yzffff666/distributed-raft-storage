# Raft Storage 自动扩缩容配置

本目录包含了Raft分布式存储系统的自动扩缩容配置，支持HPA（水平Pod自动扩缩容）和VPA（垂直Pod自动扩缩容）。

## 功能特性

- 🔄 **水平自动扩缩容**: 基于CPU、内存和自定义指标的Pod数量自动调整
- 📈 **垂直自动扩缩容**: 自动调整Pod的资源请求和限制
- 📊 **自定义指标**: 支持API QPS、响应时间、缓存命中率等业务指标
- ⏰ **智能策略**: 根据时间段自动切换扩缩容策略
- 🎯 **多维度监控**: 集成Prometheus监控和告警

## 文件结构

```
k8s/hpa/
├── api-hpa.yaml              # API服务和Redis的HPA配置
├── vpa.yaml                  # VPA配置
├── custom-metrics-config.yaml # 自定义指标配置
├── scaling-policies.yaml     # 扩缩容策略配置
├── README.md                 # 使用文档
└── ../deploy-autoscaling.sh  # 部署脚本
```

## 快速开始

### 1. 部署自动扩缩容

```bash
# 进入项目根目录
cd /root/DistributeSystem0610

# 运行部署脚本
./k8s/deploy-autoscaling.sh
```

### 2. 验证部署

```bash
# 检查HPA状态
kubectl get hpa -n raft-storage

# 检查VPA状态（如果已安装）
kubectl get vpa -n raft-storage

# 查看扩缩容事件
kubectl describe hpa raft-api-hpa -n raft-storage
```

## 配置详解

### HPA配置

#### API服务HPA (`api-hpa.yaml`)

```yaml
spec:
  minReplicas: 2      # 最小副本数
  maxReplicas: 10     # 最大副本数
  metrics:
    # CPU利用率目标：70%
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    
    # 内存利用率目标：80%
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    
    # 自定义指标：API QPS目标50/秒
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "50"
```

#### 扩缩容行为配置

```yaml
behavior:
  scaleUp:
    stabilizationWindowSeconds: 60   # 扩容稳定窗口1分钟
    policies:
      - type: Percent
        value: 100                   # 每15秒最多扩容100%
        periodSeconds: 15
      - type: Pods
        value: 4                     # 每15秒最多扩容4个Pod
        periodSeconds: 15
    selectPolicy: Max                # 选择最激进策略
  
  scaleDown:
    stabilizationWindowSeconds: 300  # 缩容稳定窗口5分钟
    policies:
      - type: Percent
        value: 50                    # 每分钟最多缩容50%
        periodSeconds: 60
      - type: Pods
        value: 2                     # 每分钟最多缩容2个Pod
        periodSeconds: 60
    selectPolicy: Min                # 选择最保守策略
```

### VPA配置

#### 自动资源调整 (`vpa.yaml`)

```yaml
spec:
  updatePolicy:
    updateMode: "Auto"              # 自动更新模式
  resourcePolicy:
    containerPolicies:
    - containerName: api
      minAllowed:
        cpu: 100m                   # 最小CPU请求
        memory: 128Mi               # 最小内存请求
      maxAllowed:
        cpu: 2000m                  # 最大CPU限制
        memory: 4Gi                 # 最大内存限制
      controlledResources: ["cpu", "memory"]
      controlledValues: RequestsAndLimits
```

### 自定义指标配置

#### Prometheus Adapter配置

支持以下自定义指标：

- `http_requests_per_second`: API请求速率
- `http_request_duration_seconds`: API响应时间P95
- `redis_connected_clients`: Redis连接数
- `raft_storage_operations_per_second`: 存储操作速率
- `cache_hit_ratio`: 缓存命中率

#### ServiceMonitor配置

自动采集以下指标端点：

- `/api/v1/actuator/prometheus`: Spring Boot Actuator指标
- `/api/v1/monitoring/metrics`: 自定义业务指标
- `/metrics`: Redis指标（如果启用）

### 智能扩缩容策略

#### 时间段策略

- **工作时间** (9:00-18:00): 较激进的扩缩容策略
- **非工作时间** (18:00-9:00): 较保守的扩缩容策略
- **高峰时段**: 节假日和促销期间的特殊策略

#### 策略配置示例

```yaml
scaling_policies:
  api_service:
    business_hours:
      min_replicas: 3
      max_replicas: 15
      target_cpu: 60
      target_memory: 70
    
    off_hours:
      min_replicas: 2
      max_replicas: 8
      target_cpu: 80
      target_memory: 85
    
    peak_hours:
      min_replicas: 5
      max_replicas: 20
      target_cpu: 50
      target_memory: 60
```

## 监控和告警

### Prometheus告警规则

配置了以下告警规则：

- **HighCPUUsage**: CPU使用率超过80%
- **HighMemoryUsage**: 内存使用率超过85%
- **HighAPILatency**: API P95响应时间超过1秒
- **LowCacheHitRate**: 缓存命中率低于70%

### 查看告警

```bash
# 查看PrometheusRule
kubectl get prometheusrule -n raft-storage

# 查看告警详情
kubectl describe prometheusrule raft-storage-scaling-rules -n raft-storage
```

## 运维操作

### 监控扩缩容状态

```bash
# 实时监控HPA状态
kubectl get hpa -n raft-storage -w

# 查看扩缩容事件
kubectl describe hpa raft-api-hpa -n raft-storage

# 监控Pod变化
kubectl get pods -n raft-storage -w

# 查看资源使用情况
kubectl top pods -n raft-storage
kubectl top nodes
```

### 手动调整扩缩容

```bash
# 临时调整最小副本数
kubectl patch hpa raft-api-hpa -n raft-storage -p '{"spec":{"minReplicas":5}}'

# 临时调整最大副本数
kubectl patch hpa raft-api-hpa -n raft-storage -p '{"spec":{"maxReplicas":20}}'

# 临时调整CPU目标
kubectl patch hpa raft-api-hpa -n raft-storage -p '{"spec":{"metrics":[{"type":"Resource","resource":{"name":"cpu","target":{"type":"Utilization","averageUtilization":50}}}]}}'
```

### 暂停/恢复自动扩缩容

```bash
# 暂停HPA
kubectl patch hpa raft-api-hpa -n raft-storage -p '{"spec":{"minReplicas":3,"maxReplicas":3}}'

# 恢复HPA
kubectl patch hpa raft-api-hpa -n raft-storage -p '{"spec":{"minReplicas":2,"maxReplicas":10}}'

# 删除HPA（手动控制副本数）
kubectl delete hpa raft-api-hpa -n raft-storage
```

### 压力测试

```bash
# 创建负载生成器
kubectl run load-generator --image=busybox --rm -i --tty --restart=Never -- /bin/sh

# 在容器内执行（生成API负载）
while true; do
  wget -q -O- http://raft-api-service.raft-storage.svc.cluster.local/api/v1/monitoring/health
  sleep 0.1
done

# 在另一个终端监控扩缩容
kubectl get hpa -n raft-storage -w
kubectl get pods -n raft-storage -w
```

### 查看VPA建议

```bash
# 查看VPA建议（如果安装了VPA）
kubectl describe vpa -n raft-storage

# 查看VPA状态
kubectl get vpa -n raft-storage -o yaml
```

## 故障排查

### 常见问题

#### 1. HPA无法获取指标

```bash
# 检查Metrics Server状态
kubectl get pods -n kube-system -l k8s-app=metrics-server

# 检查指标API可用性
kubectl get --raw "/apis/metrics.k8s.io/v1beta1/nodes"
kubectl get --raw "/apis/metrics.k8s.io/v1beta1/pods"

# 检查自定义指标API
kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1"
```

#### 2. 自定义指标不可用

```bash
# 检查Prometheus Adapter
kubectl get pods -n monitoring -l app.kubernetes.io/name=prometheus-adapter

# 检查ServiceMonitor
kubectl get servicemonitor -n raft-storage

# 检查Prometheus配置
kubectl get prometheus -n monitoring -o yaml
```

#### 3. VPA不工作

```bash
# 检查VPA组件
kubectl get pods -n kube-system -l app=vpa-recommender
kubectl get pods -n kube-system -l app=vpa-updater
kubectl get pods -n kube-system -l app=vpa-admission-controller

# 检查VPA CRD
kubectl get crd verticalpodautoscalers.autoscaling.k8s.io
```

### 日志收集

```bash
# HPA控制器日志
kubectl logs -n kube-system -l app=kube-controller-manager | grep horizontal-pod-autoscaler

# Metrics Server日志
kubectl logs -n kube-system -l k8s-app=metrics-server

# VPA日志（如果安装）
kubectl logs -n kube-system -l app=vpa-recommender
```

## 性能调优

### 扩缩容参数调优

根据应用特性调整以下参数：

```yaml
# 快速响应场景
behavior:
  scaleUp:
    stabilizationWindowSeconds: 30    # 缩短稳定窗口
    policies:
      - type: Percent
        value: 200                    # 增加扩容幅度
        periodSeconds: 10

# 稳定性优先场景
behavior:
  scaleDown:
    stabilizationWindowSeconds: 600   # 延长稳定窗口
    policies:
      - type: Percent
        value: 25                     # 减少缩容幅度
        periodSeconds: 120
```

### 指标阈值调优

```yaml
# 高性能要求
metrics:
- type: Resource
  resource:
    name: cpu
    target:
      type: Utilization
      averageUtilization: 50          # 降低CPU阈值

# 成本优化
metrics:
- type: Resource
  resource:
    name: cpu
    target:
      type: Utilization
      averageUtilization: 85          # 提高CPU阈值
```

## 最佳实践

1. **监控优先**: 先部署监控，再配置自动扩缩容
2. **渐进调优**: 从保守策略开始，逐步调优参数
3. **多指标结合**: 同时使用资源指标和业务指标
4. **测试验证**: 在测试环境充分验证扩缩容策略
5. **告警配置**: 配置扩缩容相关告警
6. **定期回顾**: 定期回顾和调整扩缩容策略

## 安全考虑

1. **RBAC权限**: 确保扩缩容相关权限最小化
2. **资源限制**: 设置合理的最大副本数限制
3. **成本控制**: 监控资源使用成本
4. **故障隔离**: 避免扩缩容影响系统稳定性

## 参考资料

- [Kubernetes HPA文档](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [VPA文档](https://github.com/kubernetes/autoscaler/tree/master/vertical-pod-autoscaler)
- [Prometheus Adapter](https://github.com/kubernetes-sigs/prometheus-adapter)
- [自定义指标API](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/#autoscaling-on-multiple-metrics-and-custom-metrics) 