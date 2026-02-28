# Harbor 私有镜像仓库配置指南

## 📋 概述

Harbor是一个开源的云原生镜像仓库，为企业提供安全、可靠的镜像存储和分发服务。

## 🚀 Harbor 部署

### 1. 环境要求

- Docker 20.10+
- Docker Compose 1.29+
- 至少4GB内存
- 至少40GB磁盘空间

### 2. 下载Harbor

```bash
# 下载Harbor离线安装包
wget https://github.com/goharbor/harbor/releases/download/v2.8.0/harbor-offline-installer-v2.8.0.tgz

# 解压
tar xvf harbor-offline-installer-v2.8.0.tgz
cd harbor
```

### 3. 配置Harbor

```bash
# 复制配置文件
cp harbor.yml.tmpl harbor.yml

# 编辑配置文件
vim harbor.yml
```

#### 主要配置项：

```yaml
# Harbor主机名
hostname: harbor.example.com

# HTTP端口
http:
  port: 80

# HTTPS配置
https:
  port: 443
  certificate: /data/cert/harbor.example.com.crt
  private_key: /data/cert/harbor.example.com.key

# Harbor管理员密码
harbor_admin_password: Harbor12345

# 数据库配置
database:
  password: root123
  max_idle_conns: 100
  max_open_conns: 900

# 数据存储路径
data_volume: /data

# 日志配置
log:
  level: info
  local:
    rotate_count: 50
    rotate_size: 200M
    location: /var/log/harbor

# 自动GC配置
jobservice:
  max_job_workers: 10

# 通知配置
notification:
  webhook_job_max_retry: 3

# Chart仓库配置
chart:
  absolute_url: disabled

# 镜像扫描配置
trivy:
  ignore_unfixed: false
  skip_update: false
  insecure: false
```

### 4. 安装Harbor

```bash
# 生成配置文件
sudo ./prepare

# 启动Harbor (包含Chart服务)
sudo ./install.sh --with-chartmuseum

# 验证安装
docker-compose ps
```

## 🔧 Harbor 配置

### 1. 创建项目

```bash
# 登录Harbor Web界面
# 默认用户名: admin
# 默认密码: Harbor12345 (或配置文件中设置的密码)

# 创建项目
curl -X POST "https://harbor.example.com/api/v2.0/projects" \
  -H "authorization: Basic YWRtaW46SGFyYm9yMTIzNDU=" \
  -H "content-type: application/json" \
  -d '{
    "project_name": "raft-storage",
    "public": false,
    "metadata": {
      "auto_scan": "true",
      "severity": "critical"
    }
  }'
```

### 2. 配置用户和权限

```bash
# 创建用户
curl -X POST "https://harbor.example.com/api/v2.0/users" \
  -H "authorization: Basic YWRtaW46SGFyYm9yMTIzNDU=" \
  -H "content-type: application/json" \
  -d '{
    "username": "ci-cd",
    "email": "ci-cd@example.com",
    "password": "CiCd123456",
    "realname": "CI/CD User"
  }'

# 添加项目成员
curl -X POST "https://harbor.example.com/api/v2.0/projects/raft-storage/members" \
  -H "authorization: Basic YWRtaW46SGFyYm9yMTIzNDU=" \
  -H "content-type: application/json" \
  -d '{
    "role_id": 2,
    "member_user": {
      "username": "ci-cd"
    }
  }'
```

### 3. 配置扫描策略

```bash
# 创建扫描策略
curl -X POST "https://harbor.example.com/api/v2.0/system/scanAll/schedule" \
  -H "authorization: Basic YWRtaW46SGFyYm9yMTIzNDU=" \
  -H "content-type: application/json" \
  -d '{
    "schedule": {
      "type": "Daily",
      "cron": "0 2 * * *"
    }
  }'
```

## 🔐 SSL证书配置

### 1. 生成自签名证书

```bash
# 创建证书目录
sudo mkdir -p /data/cert

# 生成私钥
sudo openssl genrsa -out /data/cert/harbor.example.com.key 4096

# 生成证书请求
sudo openssl req -new -key /data/cert/harbor.example.com.key \
  -out /data/cert/harbor.example.com.csr \
  -subj "/C=CN/ST=Beijing/L=Beijing/O=Example/OU=IT/CN=harbor.example.com"

# 生成证书
sudo openssl x509 -req -days 365 \
  -in /data/cert/harbor.example.com.csr \
  -signkey /data/cert/harbor.example.com.key \
  -out /data/cert/harbor.example.com.crt

# 设置权限
sudo chmod 600 /data/cert/harbor.example.com.key
sudo chmod 644 /data/cert/harbor.example.com.crt
```

### 2. 配置Docker客户端信任证书

```bash
# 复制证书到Docker证书目录
sudo mkdir -p /etc/docker/certs.d/harbor.example.com
sudo cp /data/cert/harbor.example.com.crt /etc/docker/certs.d/harbor.example.com/

# 重启Docker
sudo systemctl restart docker
```

## 🐳 Docker客户端配置

### 1. 登录Harbor

```bash
# 登录Harbor
docker login harbor.example.com
# 输入用户名和密码

# 验证登录
docker info
```

### 2. 推送镜像

```bash
# 标记镜像
docker tag raft-storage/api:latest harbor.example.com/raft-storage/api:latest

# 推送镜像
docker push harbor.example.com/raft-storage/api:latest

# 拉取镜像
docker pull harbor.example.com/raft-storage/api:latest
```

## 🔧 Jenkins集成

### 1. 安装Docker Pipeline插件

在Jenkins中安装以下插件：
- Docker Pipeline
- Docker Commons
- Harbor Plugin (可选)

### 2. 配置Harbor凭据

```groovy
// 在Jenkins中添加Harbor凭据
pipeline {
    agent any
    environment {
        HARBOR_CREDENTIALS = credentials('harbor-credentials')
        HARBOR_URL = 'harbor.example.com'
    }
    stages {
        stage('Push to Harbor') {
            steps {
                script {
                    docker.withRegistry("https://${HARBOR_URL}", HARBOR_CREDENTIALS) {
                        def image = docker.build("${HARBOR_URL}/raft-storage/api:${BUILD_NUMBER}")
                        image.push()
                        image.push('latest')
                    }
                }
            }
        }
    }
}
```

## 📊 监控和维护

### 1. Harbor健康检查

```bash
# 检查Harbor服务状态
docker-compose ps

# 检查Harbor日志
docker-compose logs -f

# 检查磁盘使用情况
df -h /data
```

### 2. 清理策略

```bash
# 配置镜像清理策略
curl -X POST "https://harbor.example.com/api/v2.0/projects/raft-storage/metadatas" \
  -H "authorization: Basic YWRtaW46SGFyYm9yMTIzNDU=" \
  -H "content-type: application/json" \
  -d '{
    "retention_id": "1",
    "auto_scan": "true"
  }'
```

### 3. 备份恢复

```bash
# 备份Harbor数据
sudo tar -czf harbor-backup-$(date +%Y%m%d).tar.gz /data

# 恢复Harbor数据
sudo tar -xzf harbor-backup-20231201.tar.gz -C /
```

## 🚨 故障排查

### 常见问题

1. **证书问题**
   ```bash
   # 检查证书有效性
   openssl x509 -in /data/cert/harbor.example.com.crt -text -noout
   ```

2. **磁盘空间不足**
   ```bash
   # 清理未使用的镜像
   docker system prune -a -f
   ```

3. **数据库连接问题**
   ```bash
   # 检查数据库日志
   docker-compose logs harbor-db
   ```

4. **网络问题**
   ```bash
   # 检查端口占用
   netstat -tlnp | grep :80
   netstat -tlnp | grep :443
   ```

## 📚 参考资料

- [Harbor官方文档](https://goharbor.io/docs/)
- [Harbor API文档](https://goharbor.io/docs/2.8.0/build-customize-contribute/configure-swagger/)
- [Docker Registry API](https://docs.docker.com/registry/spec/api/) 