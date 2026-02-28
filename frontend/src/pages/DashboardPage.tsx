import React, { useState, useEffect } from 'react';
import { Layout, Card, Row, Col, Statistic, Progress, Button, message, Typography, Space, Tabs } from 'antd';
import { 
  DatabaseOutlined, 
  CloudServerOutlined, 
  DashboardOutlined,
  ReloadOutlined,
  SettingOutlined,
  BarChartOutlined,
  MonitorOutlined
} from '@ant-design/icons';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import apiService from '../services/api';
import webSocketService from '../services/websocket';
import DataVisualization from '../components/DataVisualization';

const { Header, Content } = Layout;
const { Title, Text } = Typography;

interface DashboardData {
  systemMetrics: {
    cpuUsage: number;
    memoryUsage: number;
    diskUsage: number;
    networkIO: {
      bytesIn: number;
      bytesOut: number;
    };
  };
  cacheStats: {
    hitCount: number;
    missCount: number;
    hitRate: number;
    keyCount: number;
  };
  clusterInfo: {
    nodeCount: number;
    leader: string;
    status: string;
  };
  storageStats: {
    totalKeys: number;
    totalSize: number;
    replicationFactor: number;
  };
}

const DashboardPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
  const [realtimeData, setRealtimeData] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    loadDashboardData();
    setupWebSocket();
    
    // 定时刷新数据
    const interval = setInterval(loadDashboardData, 30000);
    
    return () => {
      clearInterval(interval);
      webSocketService.unsubscribeFromMetrics();
    };
  }, []);

  const loadDashboardData = async () => {
    try {
      setLoading(true);
      
      const [systemMetrics, cacheStats, clusterStatus, storageKeys] = await Promise.all([
        apiService.getSystemMetrics(),
        apiService.getCacheStats(),
        apiService.getClusterStatus(),
        apiService.getAllKeys()
      ]);

      const data: DashboardData = {
        systemMetrics: systemMetrics.data,
        cacheStats: cacheStats.data,
        clusterInfo: {
          nodeCount: clusterStatus.data.nodes.length,
          leader: clusterStatus.data.leader,
          status: 'healthy'
        },
        storageStats: {
          totalKeys: storageKeys.data.length,
          totalSize: 0, // 需要后端提供
          replicationFactor: 3
        }
      };

      setDashboardData(data);
    } catch (error) {
      message.error('加载仪表板数据失败');
    } finally {
      setLoading(false);
    }
  };

  const setupWebSocket = () => {
    webSocketService.subscribeToMetrics();
    
    webSocketService.on('realtime_metrics', (data: any) => {
      setRealtimeData(prev => {
        const newData = [...prev, {
          time: new Date().toLocaleTimeString(),
          cpu: data.systemMetrics.cpuUsage,
          memory: data.systemMetrics.memoryUsage,
          cacheHitRate: data.cacheMetrics.hitRate
        }];
        
        // 只保留最近20个数据点
        return newData.slice(-20);
      });
    });
  };

  const handleLogout = async () => {
    try {
      await apiService.logout();
      message.success('退出成功');
      window.location.href = '/';
    } catch (error) {
      message.error('退出失败');
    }
  };

  const pieData = [
    { name: '缓存命中', value: dashboardData?.cacheStats.hitCount || 0, color: '#52c41a' },
    { name: '缓存未命中', value: dashboardData?.cacheStats.missCount || 0, color: '#ff4d4f' }
  ];

  const tabItems = [
    {
      key: 'overview',
      label: (
        <span>
          <MonitorOutlined />
          系统概览
        </span>
      ),
      children: (
        <div>
          {/* 核心指标卡片 */}
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="存储键数量"
                  value={dashboardData?.storageStats.totalKeys || 0}
                  prefix={<DatabaseOutlined />}
                  valueStyle={{ color: '#3f8600' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="集群节点数"
                  value={dashboardData?.clusterInfo.nodeCount || 0}
                  prefix={<CloudServerOutlined />}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="缓存命中率"
                  value={dashboardData?.cacheStats.hitRate || 0}
                  suffix="%"
                  precision={2}
                  valueStyle={{ color: '#722ed1' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic
                  title="副本因子"
                  value={dashboardData?.storageStats.replicationFactor || 0}
                  valueStyle={{ color: '#eb2f96' }}
                />
              </Card>
            </Col>
          </Row>

          {/* 系统资源使用情况 */}
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            <Col xs={24} lg={12}>
              <Card title="系统资源使用率" loading={loading}>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <div>
                    <Text>CPU使用率</Text>
                    <Progress 
                      percent={dashboardData?.systemMetrics.cpuUsage || 0} 
                      status={dashboardData?.systemMetrics.cpuUsage! > 80 ? 'exception' : 'active'}
                    />
                  </div>
                  <div>
                    <Text>内存使用率</Text>
                    <Progress 
                      percent={dashboardData?.systemMetrics.memoryUsage || 0}
                      status={dashboardData?.systemMetrics.memoryUsage! > 80 ? 'exception' : 'active'}
                    />
                  </div>
                  <div>
                    <Text>磁盘使用率</Text>
                    <Progress 
                      percent={dashboardData?.systemMetrics.diskUsage || 0}
                      status={dashboardData?.systemMetrics.diskUsage! > 80 ? 'exception' : 'active'}
                    />
                  </div>
                </Space>
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card title="缓存命中统计">
                <ResponsiveContainer width="100%" height={200}>
                  <PieChart>
                    <Pie
                      data={pieData}
                      cx="50%"
                      cy="50%"
                      outerRadius={80}
                      dataKey="value"
                      label={({ name, percent }) => `${name} ${((percent || 0) * 100).toFixed(0)}%`}
                    >
                      {pieData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </Card>
            </Col>
          </Row>

          {/* 实时性能图表 */}
          <Row gutter={[16, 16]}>
            <Col xs={24}>
              <Card title="实时性能监控" extra={
                <Text type="secondary">
                  WebSocket连接: {webSocketService.isSocketConnected() ? '已连接' : '未连接'}
                </Text>
              }>
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={realtimeData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="time" />
                    <YAxis />
                    <Tooltip />
                    <Line type="monotone" dataKey="cpu" stroke="#8884d8" name="CPU使用率%" />
                    <Line type="monotone" dataKey="memory" stroke="#82ca9d" name="内存使用率%" />
                    <Line type="monotone" dataKey="cacheHitRate" stroke="#ffc658" name="缓存命中率%" />
                  </LineChart>
                </ResponsiveContainer>
              </Card>
            </Col>
          </Row>

          {/* 集群状态 */}
          <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
            <Col xs={24}>
              <Card title="集群状态信息">
                <Row gutter={[16, 16]}>
                  <Col xs={24} sm={8}>
                    <Card size="small">
                      <Statistic
                        title="当前领导者"
                        value={dashboardData?.clusterInfo.leader || 'Unknown'}
                        valueStyle={{ fontSize: 16 }}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} sm={8}>
                    <Card size="small">
                      <Statistic
                        title="集群状态"
                        value={dashboardData?.clusterInfo.status || 'Unknown'}
                        valueStyle={{ 
                          fontSize: 16,
                          color: dashboardData?.clusterInfo.status === 'healthy' ? '#52c41a' : '#ff4d4f'
                        }}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} sm={8}>
                    <Card size="small">
                      <Statistic
                        title="活跃节点"
                        value={`${dashboardData?.clusterInfo.nodeCount || 0}/3`}
                        valueStyle={{ fontSize: 16 }}
                      />
                    </Card>
                  </Col>
                </Row>
              </Card>
            </Col>
          </Row>
        </div>
      )
    },
    {
      key: 'analytics',
      label: (
        <span>
          <BarChartOutlined />
          数据分析
        </span>
      ),
      children: <DataVisualization height={800} />
    }
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ 
        background: '#fff', 
        padding: '0 24px', 
        display: 'flex', 
        justifyContent: 'space-between',
        alignItems: 'center',
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <DashboardOutlined style={{ fontSize: 24, marginRight: 12, color: '#1890ff' }} />
          <Title level={3} style={{ margin: 0 }}>分布式存储系统控制台</Title>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadDashboardData} loading={loading}>
            刷新
          </Button>
          <Button icon={<SettingOutlined />}>设置</Button>
          <Button type="default" onClick={() => window.location.href = "/storage"}>存储管理</Button>
          <Button type="primary" onClick={handleLogout}>退出</Button>
        </Space>
      </Header>

      <Content style={{ padding: 24, background: '#f0f2f5' }}>
        <Card>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            size="large"
            items={tabItems}
          />
        </Card>
      </Content>
    </Layout>
  );
};

export default DashboardPage;
