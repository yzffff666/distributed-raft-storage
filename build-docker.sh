#!/bin/bash

# 分布式存储系统Docker构建脚本

set -e

echo "🚀 开始构建分布式存储系统Docker镜像..."

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker服务"
    exit 1
fi

# 清理Maven构建缓存
echo "🧹 清理Maven构建缓存..."
mvn clean

# 构建Spring Boot API模块
echo "🔨 构建Spring Boot API模块..."
mvn package -pl spring-boot-api -DskipTests

# 构建Raft核心模块
echo "🔨 构建Raft核心模块..."
mvn package -pl distribute-java-core -DskipTests

# 构建Docker镜像
echo "🐳 构建Docker镜像..."

# 构建API服务镜像
docker build -t raft-api:latest ./spring-boot-api

# 构建Raft核心服务镜像
docker build -t raft-core:latest ./distribute-java-core

echo "✅ Docker镜像构建完成！"

# 显示构建的镜像
echo "📋 构建的镜像列表："
docker images | grep -E "(raft-api|raft-core)"

echo ""
echo "🎉 构建完成！可以使用以下命令启动服务："
echo "   docker-compose up -d"
echo ""
echo "📊 监控面板地址："
echo "   - API文档: http://localhost:8080/swagger-ui/index.html"
echo "   - 健康检查: http://localhost:8080/api/v1/monitoring/health"
echo "   - Prometheus: http://localhost:9090"
echo "   - Grafana: http://localhost:3000 (admin/admin123)"
