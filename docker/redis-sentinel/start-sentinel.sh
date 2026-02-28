#!/bin/bash

# Redis Sentinel 部署脚本

echo "🚀 启动Redis Sentinel高可用集群..."

# 检查Docker和Docker Compose
if ! command -v docker &> /dev/null; then
    echo "❌ Docker未安装，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 创建网络（如果不存在）
docker network create redis-network 2>/dev/null || true

# 启动Redis Sentinel集群
echo "📦 启动Redis主从复制和Sentinel集群..."
docker-compose up -d

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 10

# 检查服务状态
echo "🔍 检查服务状态..."
docker-compose ps

# 检查Redis主从状态
echo "📊 检查Redis主从复制状态..."
echo "Master信息:"
docker exec redis-master redis-cli info replication

echo ""
echo "Slave-1信息:"
docker exec redis-slave-1 redis-cli info replication

echo ""
echo "Slave-2信息:"
docker exec redis-slave-2 redis-cli info replication

# 检查Sentinel状态
echo ""
echo "🛡️ 检查Sentinel状态..."
echo "Sentinel-1信息:"
docker exec redis-sentinel-1 redis-cli -p 26379 sentinel masters

echo ""
echo "✅ Redis Sentinel集群启动完成！"
echo ""
echo "📝 连接信息："
echo "  Redis Master: localhost:6379"
echo "  Redis Slave-1: localhost:6380"
echo "  Redis Slave-2: localhost:6381"
echo "  Sentinel-1: localhost:26379"
echo "  Sentinel-2: localhost:26380"
echo "  Sentinel-3: localhost:26381"
echo ""
echo "🔧 测试命令："
echo "  # 连接Master"
echo "  redis-cli -h localhost -p 6379"
echo ""
echo "  # 查看Sentinel状态"
echo "  redis-cli -h localhost -p 26379 sentinel masters"
echo ""
echo "  # 测试故障转移（停止Master）"
echo "  docker stop redis-master" 