import { io, Socket } from 'socket.io-client';

// WebSocket事件类型
export interface WebSocketEvent {
  type: string;
  data: any;
  timestamp: number;
}

// 实时监控数据
export interface RealtimeMetrics {
  systemMetrics: {
    cpuUsage: number;
    memoryUsage: number;
    diskUsage: number;
    networkIO: {
      bytesIn: number;
      bytesOut: number;
    };
  };
  cacheMetrics: {
    hitRate: number;
    keyCount: number;
    operations: number;
  };
  clusterMetrics: {
    nodeCount: number;
    leader: string;
    status: string;
  };
  timestamp: number;
}

// 存储操作事件
export interface StorageEvent {
  operation: 'CREATE' | 'UPDATE' | 'DELETE';
  key: string;
  value?: any;
  timestamp: number;
  nodeId: string;
}

// 集群事件
export interface ClusterEvent {
  type: 'NODE_JOIN' | 'NODE_LEAVE' | 'LEADER_CHANGE' | 'CLUSTER_STATUS';
  nodeId?: string;
  leader?: string;
  nodes?: string[];
  timestamp: number;
}

class WebSocketService {
  private socket: Socket | null = null;
  private isConnected: boolean = false;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 5;
  private reconnectInterval: number = 3000;
  private eventListeners: Map<string, Function[]> = new Map();

  constructor() {
    this.initializeConnection();
  }

  private initializeConnection() {
    const wsUrl = process.env.REACT_APP_WS_URL || 'http://localhost:8080';
    
    this.socket = io(wsUrl, {
      transports: ['websocket', 'polling'],
      timeout: 10000,
      forceNew: true,
    });

    this.setupEventHandlers();
  }

  private setupEventHandlers() {
    if (!this.socket) return;

    // 连接成功
    this.socket.on('connect', () => {
      console.log('WebSocket连接成功');
      this.isConnected = true;
      this.reconnectAttempts = 0;
      this.emit('connected', { timestamp: Date.now() });
    });

    // 连接断开
    this.socket.on('disconnect', (reason) => {
      console.log('WebSocket连接断开:', reason);
      this.isConnected = false;
      this.emit('disconnected', { reason, timestamp: Date.now() });
      
      // 自动重连
      if (reason === 'io server disconnect') {
        // 服务器主动断开，不自动重连
        return;
      }
      this.attemptReconnect();
    });

    // 连接错误
    this.socket.on('connect_error', (error) => {
      console.error('WebSocket连接错误:', error);
      this.emit('error', { error: error.message, timestamp: Date.now() });
      this.attemptReconnect();
    });

    // 实时监控数据
    this.socket.on('realtime_metrics', (data: RealtimeMetrics) => {
      this.emit('realtime_metrics', data);
    });

    // 存储操作事件
    this.socket.on('storage_event', (data: StorageEvent) => {
      this.emit('storage_event', data);
    });

    // 集群状态变化
    this.socket.on('cluster_event', (data: ClusterEvent) => {
      this.emit('cluster_event', data);
    });

    // 缓存事件
    this.socket.on('cache_event', (data: any) => {
      this.emit('cache_event', data);
    });

    // 系统告警
    this.socket.on('system_alert', (data: any) => {
      this.emit('system_alert', data);
    });

    // 通用消息
    this.socket.on('message', (data: any) => {
      this.emit('message', data);
    });
  }

  private attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('WebSocket重连次数超过限制');
      this.emit('reconnect_failed', { 
        attempts: this.reconnectAttempts, 
        timestamp: Date.now() 
      });
      return;
    }

    this.reconnectAttempts++;
    console.log(`尝试重连WebSocket (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

    setTimeout(() => {
      if (this.socket && !this.isConnected) {
        this.socket.connect();
      }
    }, this.reconnectInterval * this.reconnectAttempts);
  }

  // 订阅事件
  on(event: string, callback: Function) {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(callback);
  }

  // 取消订阅事件
  off(event: string, callback?: Function) {
    if (!this.eventListeners.has(event)) return;

    if (callback) {
      const listeners = this.eventListeners.get(event)!;
      const index = listeners.indexOf(callback);
      if (index > -1) {
        listeners.splice(index, 1);
      }
    } else {
      this.eventListeners.delete(event);
    }
  }

  // 触发事件
  private emit(event: string, data: any) {
    if (!this.eventListeners.has(event)) return;

    const listeners = this.eventListeners.get(event)!;
    listeners.forEach(callback => {
      try {
        callback(data);
      } catch (error) {
        console.error('WebSocket事件处理错误:', error);
      }
    });
  }

  // 发送消息到服务器
  send(event: string, data: any) {
    if (this.socket && this.isConnected) {
      this.socket.emit(event, data);
    } else {
      console.warn('WebSocket未连接，无法发送消息');
    }
  }

  // 订阅实时监控数据
  subscribeToMetrics() {
    this.send('subscribe_metrics', { timestamp: Date.now() });
  }

  // 取消订阅实时监控数据
  unsubscribeFromMetrics() {
    this.send('unsubscribe_metrics', { timestamp: Date.now() });
  }

  // 订阅存储事件
  subscribeToStorageEvents() {
    this.send('subscribe_storage', { timestamp: Date.now() });
  }

  // 取消订阅存储事件
  unsubscribeFromStorageEvents() {
    this.send('unsubscribe_storage', { timestamp: Date.now() });
  }

  // 订阅集群事件
  subscribeToClusterEvents() {
    this.send('subscribe_cluster', { timestamp: Date.now() });
  }

  // 取消订阅集群事件
  unsubscribeFromClusterEvents() {
    this.send('unsubscribe_cluster', { timestamp: Date.now() });
  }

  // 获取连接状态
  isSocketConnected(): boolean {
    return this.isConnected;
  }

  // 手动重连
  reconnect() {
    if (this.socket) {
      this.socket.disconnect();
      this.socket.connect();
    }
  }

  // 断开连接
  disconnect() {
    if (this.socket) {
      this.socket.disconnect();
      this.isConnected = false;
    }
  }

  // 销毁连接
  destroy() {
    this.disconnect();
    this.eventListeners.clear();
    this.socket = null;
  }
}

// 导出单例实例
export const webSocketService = new WebSocketService();
export default webSocketService; 