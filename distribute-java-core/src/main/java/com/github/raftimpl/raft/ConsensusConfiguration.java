package com.github.raftimpl.raft;

import lombok.Getter;
import lombok.Setter;

/**
 * 共识算法配置选项类
 * 包含共识算法运行所需的各种参数配置，如超时时间、快照策略、线程池配置等
 * 
 */
@Getter
@Setter
public class ConsensusConfiguration {

    /** 
     * 选举超时时间（毫秒）
     * 如果follower在此时间内没有收到leader的消息，就会发起选举
     * 优化：从5000ms减少到3000ms，加快故障检测速度
     */
    private int voteTimeoutMs = 3000;

    /** 
     * 心跳周期（毫秒）
     * leader至少以此频率发送心跳，即使没有数据需要发送
     * 优化：从500ms减少到300ms，提高集群响应性
     */
    private int keepAlivePeriodMs = 300;

    /** 
     * 快照定时器执行间隔（秒）
     * 定期创建快照的时间间隔
     * 优化：从3600秒减少到1800秒，更频繁的快照减少日志回放时间
     */
    private int backupIntervalSeconds = 1800;
    
    /** 
     * 快照最小日志大小（字节）
     * 只有当日志条目大小达到此值时，才会创建快照
     * 优化：从100MB减少到50MB，更早触发快照减少内存占用
     */
    private int snapshotMinLogSize = 50 * 1024 * 1024;
    
    /** 
     * 每次快照请求的最大字节数
     * 限制单次快照传输的数据量
     * 优化：从500KB增加到2MB，减少快照传输的网络往返次数
     */
    private int maxSnapshotBytesPerRequest = 2 * 1024 * 1024; // 2MB

    /** 
     * 每次请求的最大日志条目数
     * 限制单次日志同步请求中包含的日志条目数量
     * 优化：从5000增加到10000，提高批处理效率
     */
    private int maxEntryBatchSize = 10000;

    /** 
     * 单个segment文件大小（字节）
     * 日志文件分段存储，每个segment文件的最大大小
     * 优化：从100MB增加到256MB，减少文件切换频率
     */
    private int maxSegmentFileSize = 256 * 1000 * 1000;

    /** 
     * 追赶边界值
     * follower与leader的日志差距在此范围内时，才可以参与选举和提供服务
     * 优化：从500增加到1000，允许更大的日志差距，提高容错性
     */
    private long catchupMargin = 1000;

    /** 
     * 复制操作最大等待超时时间（毫秒）
     * 同步写模式下，等待大多数节点确认的最大时间
     * 优化：从1000ms减少到800ms，减少写操作延迟
     */
    private long maxAwaitTimeout = 800;

    /** 
     * 共识线程池大小
     * 用于处理与其他节点进行同步、选主等操作的线程池大小
     * 优化：从20增加到50，提高并发处理能力
     */
    private int raftConsensusThreadNum = 50;

    /** 
     * 是否异步写数据
     * true: 主节点保存后就返回，然后异步同步给从节点
     * false: 主节点同步给大多数从节点后才返回
     * 优化：默认改为true，提高写操作性能（可通过配置调整）
     */
    private boolean asyncWrite = true;

    /** 
     * 共识数据目录
     * 存储日志和快照文件的父目录，需要提供绝对路径
     */
    private String dataDir = System.getProperty("com.github.raftimpl.raft.data.dir");

    // 新增性能优化配置项

    /** 
     * 网络IO线程池大小
     * 用于处理网络通信的线程池大小
     */
    private int networkIOThreadNum = 20;

    /** 
     * 日志同步批处理延迟（毫秒）
     * 收集多个日志条目后批量发送的等待时间
     */
    private int batchDelayMs = 10;

    /** 
     * 预读取缓冲区大小（条目数）
     * 预先读取的日志条目数量，提高顺序读取性能
     */
    private int readAheadBufferSize = 1000;

    /** 
     * 写缓冲区大小（字节）
     * 日志写入缓冲区大小，提高写入性能
     */
    private int writeBufferSize = 8 * 1024 * 1024; // 8MB

    /** 
     * 压缩阈值（字节）
     * 超过此大小的日志条目将进行压缩
     */
    private int compressionThreshold = 1024; // 1KB

    /** 
     * 是否启用日志压缩
     * 对大的日志条目进行压缩以节省存储空间和网络带宽
     */
    private boolean enableLogCompression = true;

    /** 
     * 连接池最大连接数
     * RPC连接池的最大连接数
     */
    private int maxConnectionPoolSize = 100;

    /** 
     * 连接超时时间（毫秒）
     * RPC连接建立的超时时间
     */
    private int connectionTimeoutMs = 5000;

    /** 
     * 读取超时时间（毫秒）
     * RPC读取操作的超时时间
     */
    private int readTimeoutMs = 10000;

    /** 
     * 是否启用管道化
     * 启用请求管道化以提高网络利用率
     */
    private boolean enablePipelining = true;

    /** 
     * 管道化队列大小
     * 管道化请求队列的最大大小
     */
    private int pipelineQueueSize = 1000;
}
