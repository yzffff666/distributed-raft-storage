import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Select, DatePicker, Space, Typography, Progress } from 'antd';
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  RadialBarChart,
  RadialBar
} from 'recharts';
import {
  DatabaseOutlined,
  CloudServerOutlined,
  ThunderboltOutlined,
  HddOutlined,
  GlobalOutlined,
  TrophyOutlined
} from '@ant-design/icons';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

interface DataVisualizationProps {
  height?: number;
}

const DataVisualization: React.FC<DataVisualizationProps> = ({ height = 800 }) => {
  const [timeRange, setTimeRange] = useState('24h');
  const [dataType, setDataType] = useState('all');

  // 模拟数据
  const [performanceData, setPerformanceData] = useState([
    { time: '00:00', cpu: 45, memory: 62, disk: 38, network: 25 },
    { time: '04:00', cpu: 52, memory: 58, disk: 42, network: 30 },
    { time: '08:00', cpu: 68, memory: 71, disk: 45, network: 55 },
    { time: '12:00', cpu: 75, memory: 76, disk: 48, network: 62 },
    { time: '16:00', cpu: 82, memory: 79, disk: 52, network: 68 },
    { time: '20:00', cpu: 65, memory: 67, disk: 46, network: 45 },
  ]);

  const [storageData, setStorageData] = useState([
    { name: '用户数据', value: 45, color: '#8884d8' },
    { name: '系统配置', value: 25, color: '#82ca9d' },
    { name: '缓存数据', value: 20, color: '#ffc658' },
    { name: '日志数据', value: 10, color: '#ff7300' },
  ]);

  const [operationData, setOperationData] = useState([
    { operation: 'GET', count: 1250, success: 98.5 },
    { operation: 'PUT', count: 856, success: 97.2 },
    { operation: 'DELETE', count: 234, success: 99.1 },
    { operation: 'POST', count: 445, success: 96.8 },
  ]);

  const [clusterMetrics, setClusterMetrics] = useState([
    { node: 'Node-1', cpu: 45, memory: 62, status: 'Leader' },
    { node: 'Node-2', cpu: 38, memory: 55, status: 'Follower' },
    { node: 'Node-3', cpu: 42, memory: 58, status: 'Follower' },
  ]);

  const [cacheMetrics, setCacheMetrics] = useState({
    hitRate: 89.3,
    missRate: 10.7,
    totalRequests: 15420,
    hotDataCount: 342
  });

  const [networkTraffic, setNetworkTraffic] = useState([
    { time: '00:00', inbound: 120, outbound: 85 },
    { time: '04:00', inbound: 95, outbound: 72 },
    { time: '08:00', inbound: 180, outbound: 125 },
    { time: '12:00', inbound: 220, outbound: 165 },
    { time: '16:00', inbound: 195, outbound: 142 },
    { time: '20:00', inbound: 145, outbound: 98 },
  ]);

  return (
    <div style={{ height }}>
      {/* 控制面板 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Title level={4} style={{ margin: 0 }}>数据可视化分析</Title>
          </Col>
          <Col>
            <Space>
              <Select
                value={timeRange}
                onChange={setTimeRange}
                style={{ width: 120 }}
                options={[
                  { label: '最近1小时', value: '1h' },
                  { label: '最近24小时', value: '24h' },
                  { label: '最近7天', value: '7d' },
                  { label: '最近30天', value: '30d' },
                ]}
              />
              <Select
                value={dataType}
                onChange={setDataType}
                style={{ width: 120 }}
                options={[
                  { label: '全部数据', value: 'all' },
                  { label: '性能指标', value: 'performance' },
                  { label: '存储分析', value: 'storage' },
                  { label: '网络流量', value: 'network' },
                ]}
              />
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 关键指标卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="总存储量"
              value={2.3}
              suffix="TB"
              prefix={<DatabaseOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
            <Progress percent={23} size="small" showInfo={false} />
            <Text type="secondary" style={{ fontSize: 12 }}>
              容量使用率: 23%
            </Text>
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="QPS"
              value={1247}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
            <Progress percent={62} size="small" showInfo={false} strokeColor="#1890ff" />
            <Text type="secondary" style={{ fontSize: 12 }}>
              峰值: 2000 QPS
            </Text>
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="缓存命中率"
              value={cacheMetrics.hitRate}
              suffix="%"
              precision={1}
              prefix={<TrophyOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
            <Progress percent={cacheMetrics.hitRate} size="small" showInfo={false} strokeColor="#722ed1" />
            <Text type="secondary" style={{ fontSize: 12 }}>
              总请求: {cacheMetrics.totalRequests.toLocaleString()}
            </Text>
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="网络吞吐"
              value={142}
              suffix="MB/s"
              prefix={<GlobalOutlined />}
              valueStyle={{ color: '#eb2f96' }}
            />
            <Progress percent={45} size="small" showInfo={false} strokeColor="#eb2f96" />
            <Text type="secondary" style={{ fontSize: 12 }}>
              带宽使用率: 45%
            </Text>
          </Card>
        </Col>
      </Row>

      {/* 图表区域 */}
      <Row gutter={[16, 16]}>
        {/* 系统性能趋势 */}
        <Col xs={24} lg={12}>
          <Card title="系统性能趋势" style={{ height: 350 }}>
            <ResponsiveContainer width="100%" height={280}>
              <AreaChart data={performanceData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Area type="monotone" dataKey="cpu" stackId="1" stroke="#8884d8" fill="#8884d8" name="CPU%" />
                <Area type="monotone" dataKey="memory" stackId="1" stroke="#82ca9d" fill="#82ca9d" name="内存%" />
                <Area type="monotone" dataKey="disk" stackId="1" stroke="#ffc658" fill="#ffc658" name="磁盘%" />
              </AreaChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        {/* 存储数据分布 */}
        <Col xs={24} lg={12}>
          <Card title="存储数据分布" style={{ height: 350 }}>
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie
                  data={storageData}
                  cx="50%"
                  cy="50%"
                  outerRadius={80}
                  dataKey="value"
                  label={({ name, percent }) => `${name} ${((percent || 0) * 100).toFixed(0)}%`}
                >
                  {storageData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        {/* 操作统计 */}
        <Col xs={24} lg={12}>
          <Card title="操作类型统计" style={{ height: 350 }}>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={operationData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="operation" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="count" fill="#8884d8" name="请求数量" />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        {/* 网络流量 */}
        <Col xs={24} lg={12}>
          <Card title="网络流量趋势" style={{ height: 350 }}>
            <ResponsiveContainer width="100%" height={280}>
              <LineChart data={networkTraffic}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="inbound" stroke="#8884d8" name="入站 (MB/s)" />
                <Line type="monotone" dataKey="outbound" stroke="#82ca9d" name="出站 (MB/s)" />
              </LineChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        {/* 集群节点状态 */}
        <Col xs={24}>
          <Card title="集群节点状态">
            <Row gutter={[16, 16]}>
              {clusterMetrics.map((node, index) => (
                <Col xs={24} sm={8} key={index}>
                  <Card size="small">
                    <div style={{ textAlign: 'center' }}>
                      <CloudServerOutlined style={{ fontSize: 32, color: node.status === 'Leader' ? '#1890ff' : '#52c41a', marginBottom: 8 }} />
                      <Title level={5}>{node.node}</Title>
                      <Text type="secondary">{node.status}</Text>
                      <div style={{ marginTop: 16 }}>
                        <div style={{ marginBottom: 8 }}>
                          <Text>CPU: {node.cpu}%</Text>
                          <Progress percent={node.cpu} size="small" />
                        </div>
                        <div>
                          <Text>内存: {node.memory}%</Text>
                          <Progress percent={node.memory} size="small" />
                        </div>
                      </div>
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default DataVisualization;
