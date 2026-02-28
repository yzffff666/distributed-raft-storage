import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 自定义指标
const errorRate = new Rate('errors');
const apiResponseTime = new Trend('api_response_time');

// 测试配置
export const options = {
  stages: [
    { duration: '2m', target: 10 }, // 预热阶段
    { duration: '5m', target: 50 }, // 负载增加
    { duration: '10m', target: 100 }, // 稳定负载
    { duration: '5m', target: 200 }, // 峰值负载
    { duration: '2m', target: 0 }, // 负载降低
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95%的请求响应时间小于500ms
    http_req_failed: ['rate<0.1'], // 错误率小于10%
    errors: ['rate<0.1'], // 自定义错误率小于10%
  },
};

// 测试环境配置
const BASE_URL = __ENV.BASE_URL || 'https://test.raft-storage.local';
const API_VERSION = 'v1';
const API_BASE = `${BASE_URL}/api/${API_VERSION}`;

// 认证token（在实际环境中应该从环境变量获取）
let authToken = '';

// 测试数据
const testData = {
  storage: {
    key: `test-key-${Date.now()}`,
    value: 'test-value-for-performance-testing',
    keys: []
  }
};

export function setup() {
  console.log('🚀 开始性能测试...');
  
  // 登录获取token
  const loginResponse = http.post(`${API_BASE}/auth/login`, JSON.stringify({
    username: 'admin',
    password: 'admin123'
  }), {
    headers: { 'Content-Type': 'application/json' }
  });
  
  if (loginResponse.status === 200) {
    const loginData = JSON.parse(loginResponse.body);
    authToken = loginData.data.token;
    console.log('✅ 登录成功，获取到认证token');
  } else {
    console.error('❌ 登录失败:', loginResponse.status);
    throw new Error('登录失败');
  }
  
  return { authToken };
}

export default function(data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.authToken}`
  };
  
  // 测试场景权重分配
  const scenario = Math.random();
  
  if (scenario < 0.4) {
    // 40% - 存储写入测试
    testStorageWrite(headers);
  } else if (scenario < 0.7) {
    // 30% - 存储读取测试
    testStorageRead(headers);
  } else if (scenario < 0.85) {
    // 15% - 批量操作测试
    testBatchOperations(headers);
  } else if (scenario < 0.95) {
    // 10% - 集群状态查询测试
    testClusterStatus(headers);
  } else {
    // 5% - 系统监控测试
    testSystemMonitoring(headers);
  }
  
  sleep(1);
}

function testStorageWrite(headers) {
  const key = `perf-test-${Date.now()}-${Math.random()}`;
  const value = `performance-test-value-${Math.random()}`;
  
  const response = http.post(`${API_BASE}/storage`, JSON.stringify({
    key: key,
    value: value
  }), { headers });
  
  const success = check(response, {
    'storage write status is 200': (r) => r.status === 200,
    'storage write response time < 200ms': (r) => r.timings.duration < 200,
  });
  
  errorRate.add(!success);
  apiResponseTime.add(response.timings.duration);
  
  if (success) {
    testData.storage.keys.push(key);
  }
}

function testStorageRead(headers) {
  let key = testData.storage.key;
  
  // 如果有写入的key，随机选择一个
  if (testData.storage.keys.length > 0) {
    key = testData.storage.keys[Math.floor(Math.random() * testData.storage.keys.length)];
  }
  
  const response = http.get(`${API_BASE}/storage/${key}`, { headers });
  
  const success = check(response, {
    'storage read status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'storage read response time < 100ms': (r) => r.timings.duration < 100,
  });
  
  errorRate.add(!success);
  apiResponseTime.add(response.timings.duration);
}

function testBatchOperations(headers) {
  const batchData = [];
  for (let i = 0; i < 10; i++) {
    batchData.push({
      key: `batch-${Date.now()}-${i}`,
      value: `batch-value-${i}`
    });
  }
  
  const response = http.post(`${API_BASE}/storage/batch`, JSON.stringify({
    operations: batchData
  }), { headers });
  
  const success = check(response, {
    'batch operation status is 200': (r) => r.status === 200,
    'batch operation response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  errorRate.add(!success);
  apiResponseTime.add(response.timings.duration);
}

function testClusterStatus(headers) {
  const response = http.get(`${API_BASE}/cluster/status`, { headers });
  
  const success = check(response, {
    'cluster status is 200': (r) => r.status === 200,
    'cluster status response time < 100ms': (r) => r.timings.duration < 100,
    'cluster status has leader': (r) => {
      const data = JSON.parse(r.body);
      return data.data && data.data.leader;
    }
  });
  
  errorRate.add(!success);
  apiResponseTime.add(response.timings.duration);
}

function testSystemMonitoring(headers) {
  const response = http.get(`${API_BASE}/monitoring/metrics`, { headers });
  
  const success = check(response, {
    'monitoring metrics status is 200': (r) => r.status === 200,
    'monitoring metrics response time < 200ms': (r) => r.timings.duration < 200,
  });
  
  errorRate.add(!success);
  apiResponseTime.add(response.timings.duration);
}

export function teardown(data) {
  console.log('🧹 清理测试数据...');
  
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.authToken}`
  };
  
  // 清理测试过程中创建的数据
  testData.storage.keys.forEach(key => {
    http.del(`${API_BASE}/storage/${key}`, null, { headers });
  });
  
  console.log('✅ 性能测试完成');
} 