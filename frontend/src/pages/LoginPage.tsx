import React, { useState } from 'react';
import { Form, Input, Button, Card, message, Typography, Space, Divider } from 'antd';
import { UserOutlined, LockOutlined, LoginOutlined, UserAddOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import apiService from '../services/api';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
}

interface RegisterForm {
  username: string;
  password: string;
  confirmPassword: string;
  email: string;
}

const LoginPage: React.FC = () => {
  const [isLogin, setIsLogin] = useState(true);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const response = await apiService.login(values.username, values.password);
      if (response.success) {
        apiService.setToken(response.data.token);
        message.success('登录成功！');
        navigate('/dashboard');
      } else {
        message.error(response.message || '登录失败');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '登录失败，请检查用户名和密码');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (values: RegisterForm) => {
    if (values.password !== values.confirmPassword) {
      message.error('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      const response = await apiService.register(values.username, values.password, values.email);
      if (response.success) {
        message.success('注册成功！请登录');
        setIsLogin(true);
      } else {
        message.error(response.message || '注册失败');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-background">
        <div className="login-overlay" />
      </div>
      
      <Card className="login-card" bordered={false}>
        <div className="login-header">
          <Title level={2} className="login-title">
            分布式存储系统
          </Title>
          <Text type="secondary" className="login-subtitle">
            基于Raft算法的企业级分布式存储平台
          </Text>
        </div>

        <Divider />

        <div className="login-tabs">
          <Button
            type={isLogin ? 'primary' : 'text'}
            onClick={() => setIsLogin(true)}
            className="tab-button"
          >
            登录
          </Button>
          <Button
            type={!isLogin ? 'primary' : 'text'}
            onClick={() => setIsLogin(false)}
            className="tab-button"
          >
            注册
          </Button>
        </div>

        {isLogin ? (
          <Form
            name="login"
            onFinish={handleLogin}
            autoComplete="off"
            layout="vertical"
            size="large"
          >
            <Form.Item
              name="username"
              label="用户名"
              rules={[{ required: true, message: '请输入用户名!' }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="请输入用户名"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码!' }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请输入密码"
                autoComplete="current-password"
              />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                icon={<LoginOutlined />}
                block
                className="login-button"
              >
                登录
              </Button>
            </Form.Item>
          </Form>
        ) : (
          <Form
            name="register"
            onFinish={handleRegister}
            autoComplete="off"
            layout="vertical"
            size="large"
          >
            <Form.Item
              name="username"
              label="用户名"
              rules={[
                { required: true, message: '请输入用户名!' },
                { min: 3, message: '用户名至少3个字符!' },
                { max: 20, message: '用户名不能超过20个字符!' }
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="请输入用户名"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              name="email"
              label="邮箱"
              rules={[
                { required: true, message: '请输入邮箱!' },
                { type: 'email', message: '请输入有效的邮箱地址!' }
              ]}
            >
              <Input
                placeholder="请输入邮箱"
                autoComplete="email"
              />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码!' },
                { min: 6, message: '密码至少6个字符!' }
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请输入密码"
                autoComplete="new-password"
              />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认密码"
              rules={[
                { required: true, message: '请确认密码!' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致!'));
                  },
                }),
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请确认密码"
                autoComplete="new-password"
              />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                icon={<UserAddOutlined />}
                block
                className="login-button"
              >
                注册
              </Button>
            </Form.Item>
          </Form>
        )}

        <div className="login-footer">
          <Space direction="vertical" align="center" size="small">
            <Text type="secondary" className="demo-info">
              演示账号: admin / admin123
            </Text>
            <Text type="secondary" className="version-info">
              版本 v1.9.0
            </Text>
          </Space>
        </div>
      </Card>
    </div>
  );
};

export default LoginPage; 