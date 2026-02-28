import React, { useState, useRef, useEffect } from 'react';
import { 
  Card, 
  Input, 
  Button, 
  List, 
  Avatar, 
  Typography, 
  Space, 
  Spin,
  message,
  Tooltip,
  Tag
} from 'antd';
import { 
  SendOutlined, 
  RobotOutlined, 
  UserOutlined, 
  ClearOutlined,
  HistoryOutlined,
  BulbOutlined
} from '@ant-design/icons';
import apiService from '../../services/api';

const { TextArea } = Input;
const { Text, Paragraph } = Typography;

interface ChatMessage {
  id: string;
  type: 'user' | 'ai';
  content: string;
  timestamp: number;
  status?: 'sending' | 'success' | 'error';
}

interface ChatInterfaceProps {
  onSendMessage?: (message: string) => Promise<string>;
  placeholder?: string;
  height?: number;
}

const ChatInterface: React.FC<ChatInterfaceProps> = ({
  onSendMessage,
  placeholder = "请输入您的问题，我可以帮您查询存储数据、分析系统状态...",
  height = 400
}) => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 预设问题示例
  const exampleQuestions = [
    "显示当前系统状态",
    "查询缓存命中率",
    "列出所有存储的键",
    "分析集群健康状况",
    "获取优化建议"
  ];

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim() || loading) return;

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      type: 'user',
      content: inputValue.trim(),
      timestamp: Date.now(),
      status: 'success'
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setLoading(true);

    try {
      let aiResponse: string;
      
      // 使用自定义处理函数或默认AI API
      if (onSendMessage) {
        aiResponse = await onSendMessage(userMessage.content);
      } else {
        // 调用后端AI API
        const response = await apiService.processAIQuery(userMessage.content);
        if (response.success) {
          aiResponse = response.data;
        } else {
          throw new Error(response.message || 'AI服务响应失败');
        }
      }
      
      const aiMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: aiResponse,
        timestamp: Date.now(),
        status: 'success'
      };

      setMessages(prev => [...prev, aiMessage]);
    } catch (error: any) {
      message.error('AI响应失败，请稍后重试');
      
      const errorMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: '抱歉，我现在无法处理您的请求。请稍后重试或联系管理员。',
        timestamp: Date.now(),
        status: 'error'
      };

      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  const handleClearChat = () => {
    setMessages([]);
    message.success('聊天记录已清空');
  };

  const handleExampleClick = async (question: string) => {
    setInputValue(question);
    
    // 自动发送预设问题
    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      type: 'user',
      content: question,
      timestamp: Date.now(),
      status: 'success'
    };

    setMessages(prev => [...prev, userMessage]);
    setLoading(true);

    try {
      let aiResponse: string;
      
      // 根据问题类型调用不同的API
      if (question.includes('系统状态')) {
        const response = await apiService.getSystemAnalysis();
        aiResponse = response.success ? response.data : '系统分析失败';
      } else if (question.includes('优化建议')) {
        const response = await apiService.getOptimizationSuggestions();
        aiResponse = response.success ? response.data : '获取优化建议失败';
      } else {
        // 使用通用AI查询
        const response = await apiService.processAIQuery(question);
        aiResponse = response.success ? response.data : 'AI查询失败';
      }
      
      const aiMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: aiResponse,
        timestamp: Date.now(),
        status: 'success'
      };

      setMessages(prev => [...prev, aiMessage]);
    } catch (error: any) {
      message.error('AI响应失败');
      
      const errorMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        type: 'ai',
        content: '抱歉，AI服务暂时不可用。',
        timestamp: Date.now(),
        status: 'error'
      };

      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return (
    <Card 
      title={
        <Space>
          <RobotOutlined style={{ color: '#1890ff' }} />
          <span>AI智能助手</span>
          <Tag color="blue">实时连接</Tag>
        </Space>
      }
      extra={
        <Space>
          <Tooltip title="查看历史">
            <Button icon={<HistoryOutlined />} size="small" />
          </Tooltip>
          <Tooltip title="清空聊天">
            <Button icon={<ClearOutlined />} size="small" onClick={handleClearChat} />
          </Tooltip>
        </Space>
      }
      style={{ height: height + 100 }}
    >
      {/* 示例问题 */}
      {messages.length === 0 && (
        <div style={{ marginBottom: 16 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            <BulbOutlined /> 试试这些问题:
          </Text>
          <div style={{ marginTop: 8 }}>
            <Space wrap>
              {exampleQuestions.map((question, index) => (
                <Tag 
                  key={index}
                  style={{ cursor: 'pointer' }}
                  onClick={() => handleExampleClick(question)}
                >
                  {question}
                </Tag>
              ))}
            </Space>
          </div>
        </div>
      )}

      {/* 消息列表 */}
      <div style={{ 
        height: height - 120, 
        overflowY: 'auto', 
        marginBottom: 16,
        border: '1px solid #f0f0f0',
        borderRadius: 6,
        padding: 8
      }}>
        <List
          dataSource={messages}
          renderItem={(message) => (
            <List.Item style={{ border: 'none', padding: '8px 0' }}>
              <List.Item.Meta
                avatar={
                  <Avatar 
                    icon={message.type === 'user' ? <UserOutlined /> : <RobotOutlined />}
                    style={{ 
                      backgroundColor: message.type === 'user' ? '#1890ff' : '#52c41a' 
                    }}
                  />
                }
                title={
                  <Space>
                    <Text strong>
                      {message.type === 'user' ? '您' : 'AI助手'}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {new Date(message.timestamp).toLocaleTimeString()}
                    </Text>
                    {message.status === 'error' && (
                      <Tag color="red">错误</Tag>
                    )}
                  </Space>
                }
                description={
                  <div style={{ marginTop: 4 }}>
                    <Paragraph 
                      style={{ 
                        marginBottom: 0,
                        whiteSpace: 'pre-wrap',
                        backgroundColor: message.type === 'user' ? '#f6f8ff' : '#f6ffed',
                        padding: '8px 12px',
                        borderRadius: 8,
                        border: `1px solid ${message.type === 'user' ? '#d6e4ff' : '#d9f7be'}`
                      }}
                    >
                      {message.content}
                    </Paragraph>
                  </div>
                }
              />
            </List.Item>
          )}
        />
        
        {loading && (
          <div style={{ textAlign: 'center', padding: 16 }}>
            <Spin />
            <Text type="secondary" style={{ marginLeft: 8 }}>
              AI正在思考中...
            </Text>
          </div>
        )}
        
        <div ref={messagesEndRef} />
      </div>

      {/* 输入框 */}
      <div style={{ display: 'flex', gap: 8 }}>
        <TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder={placeholder}
          autoSize={{ minRows: 1, maxRows: 3 }}
          disabled={loading}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSendMessage}
          loading={loading}
          disabled={!inputValue.trim()}
        >
          发送
        </Button>
      </div>
    </Card>
  );
};

export default ChatInterface;
