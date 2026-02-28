import React, { useState } from 'react';
import { Layout, Tabs, Card, Row, Col, Button, Space, Typography } from 'antd';
import {
  DatabaseOutlined,
  RobotOutlined,
  FileOutlined,
  BarChartOutlined,
  SettingOutlined
} from '@ant-design/icons';
import ChatInterface from '../components/AI/ChatInterface';
import FileManager from '../components/FileManager';
import apiService from '../services/api';

const { Content } = Layout;
const { TabPane } = Tabs;
const { Title, Text } = Typography;

const StoragePage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('files');

  // AI聊天消息处理
  const handleAIMessage = async (message: string): Promise<string> => {
    try {
      // 这里可以集成实际的AI服务
      // 目前使用模拟响应
      return `AI响应: ${message}`;
    } catch (error) {
      throw new Error('AI服务暂时不可用');
    }
  };

  const tabItems = [
    {
      key: 'files',
      label: (
        <span>
          <FileOutlined />
          文件管理
        </span>
      ),
      children: <FileManager height={600} />
    },
    {
      key: 'ai-chat',
      label: (
        <span>
          <RobotOutlined />
          AI助手
        </span>
      ),
      children: (
        <ChatInterface
          onSendMessage={handleAIMessage}
          height={600}
          placeholder="询问存储数据、系统状态或获取智能建议..."
        />
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
      children: (
        <Card style={{ height: 600 }}>
          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center', 
            height: '100%',
            flexDirection: 'column'
          }}>
            <BarChartOutlined style={{ fontSize: 64, color: '#1890ff', marginBottom: 16 }} />
            <Title level={3}>数据分析功能</Title>
            <Text type="secondary">
              即将推出：存储使用趋势、访问模式分析、性能优化建议等功能
            </Text>
          </div>
        </Card>
      )
    },
    {
      key: 'settings',
      label: (
        <span>
          <SettingOutlined />
          存储设置
        </span>
      ),
      children: (
        <Card style={{ height: 600 }}>
          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center', 
            height: '100%',
            flexDirection: 'column'
          }}>
            <SettingOutlined style={{ fontSize: 64, color: '#1890ff', marginBottom: 16 }} />
            <Title level={3}>存储配置</Title>
            <Text type="secondary">
              即将推出：副本策略、分片配置、生命周期管理等设置
            </Text>
          </div>
        </Card>
      )
    }
  ];

  return (
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5' }}>
      <Content style={{ padding: 24 }}>
        <div style={{ marginBottom: 24 }}>
          <Row justify="space-between" align="middle">
            <Col>
              <Space>
                <DatabaseOutlined style={{ fontSize: 24, color: '#1890ff' }} />
                <Title level={2} style={{ margin: 0 }}>
                  智能存储管理
                </Title>
              </Space>
            </Col>
            <Col>
              <Space>
                <Button 
                  type="primary"
                  onClick={() => window.location.href = "/dashboard"}
                >
                  返回控制台
                </Button>
              </Space>
            </Col>
          </Row>
          <Text type="secondary">
            集成AI助手的现代化存储管理平台，支持文件上传、智能查询、数据分析等功能
          </Text>
        </div>

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

export default StoragePage;
