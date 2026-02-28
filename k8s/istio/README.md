
本目录包含了 Raft Storage 项目的 Istio 服务网格配置。

## 快速开始

```bash
# 一键部署Istio服务网格
./deploy-istio.sh

# 指定版本
./deploy-istio.sh 1.20.1
```

## 核心功能

- **流量管理**: Gateway、VirtualService、DestinationRule
- **安全策略**: mTLS、授权策略、JWT验证
- **可观测性**: 指标收集、访问日志、分布式追踪

## 访问方式

```bash
# 获取Gateway地址并配置hosts
export INGRESS_HOST=$(kubectl get service istio-ingressgateway -n istio-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "$INGRESS_HOST raft-api.local" >> /etc/hosts

# 访问服务
curl -k https://raft-api.local/api/v1/monitoring/health
```

## 监控工具

```bash
istioctl dashboard kiali    # 服务拓扑
istioctl dashboard jaeger   # 链路追踪
istioctl dashboard grafana  # 监控面板
```
