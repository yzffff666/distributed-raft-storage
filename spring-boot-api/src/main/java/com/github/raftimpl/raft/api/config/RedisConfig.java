package com.github.raftimpl.raft.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis配置类
 * 支持单机模式和集群模式，提供缓存管理和高级功能
 */
@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.database:0}")
    private int database;

    @Value("${spring.redis.cluster.nodes:}")
    private String clusterNodes;

    @Value("${spring.redis.cluster.enabled:false}")
    private boolean clusterEnabled;

    @Value("${spring.redis.timeout:3000}")
    private String timeoutStr;
    
    private int getTimeout() {
        try {
            // 如果包含"ms"后缀，去掉它
            if (timeoutStr.endsWith("ms")) {
                return Integer.parseInt(timeoutStr.substring(0, timeoutStr.length() - 2));
            }
            return Integer.parseInt(timeoutStr);
        } catch (NumberFormatException e) {
            return 3000; // 默认值
        }
    }

    @Value("${spring.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Value("${spring.redis.lettuce.pool.max-wait:-1}")
    private long maxWait;

    @Value("${spring.redis.sentinel.enabled:false}")
    private boolean sentinelEnabled;

    @Value("${spring.redis.sentinel.master:mymaster}")
    private String sentinelMaster;

    @Value("${spring.redis.sentinel.nodes:}")
    private String sentinelNodes;

    /**
     * Redis连接工厂配置
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        if (clusterEnabled && clusterNodes != null && !clusterNodes.isEmpty()) {
            // Redis集群模式
            return createClusterConnectionFactory();
        } else if (sentinelEnabled && sentinelNodes != null && !sentinelNodes.isEmpty()) {
            // Redis Sentinel模式
            return createSentinelConnectionFactory();
        } else {
            // Redis单机模式
            return createStandaloneConnectionFactory();
        }
    }

    /**
     * 创建Redis集群连接工厂
     */
    private RedisConnectionFactory createClusterConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        
        // 解析集群节点
        String[] nodes = clusterNodes.split(",");
        for (String node : nodes) {
            String[] hostPort = node.trim().split(":");
            if (hostPort.length == 2) {
                clusterConfig.clusterNode(hostPort[0], Integer.parseInt(hostPort[1]));
            }
        }
        
        // 设置集群参数
        clusterConfig.setMaxRedirects(3);
        
        // 使用Jedis连接工厂（更好的集群支持）
        JedisConnectionFactory factory = new JedisConnectionFactory(clusterConfig, jedisPoolConfig());
        factory.setTimeout(getTimeout());
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * 创建Redis Sentinel连接工厂
     */
    private RedisConnectionFactory createSentinelConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
        sentinelConfig.setMaster(sentinelMaster);
        sentinelConfig.setDatabase(database);
        
        // 解析Sentinel节点
        String[] nodes = sentinelNodes.split(",");
        for (String node : nodes) {
            String[] hostPort = node.trim().split(":");
            if (hostPort.length == 2) {
                sentinelConfig.sentinel(hostPort[0], Integer.parseInt(hostPort[1]));
            }
        }
        
        // 使用Lettuce连接工厂
        LettuceConnectionFactory factory = new LettuceConnectionFactory(sentinelConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * 创建Redis单机连接工厂
     */
    private RedisConnectionFactory createStandaloneConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(database);
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * Jedis连接池配置
     */
    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWaitMillis(maxWait);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        return poolConfig;
    }

    /**
     * RedisTemplate配置 - 通用对象缓存
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 设置key序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // 设置value序列化器
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        // 启用事务支持
        template.setEnableTransactionSupport(true);
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * StringRedisTemplate配置 - 字符串专用
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis缓存管理器配置
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))  // 默认缓存1小时
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 配置不同缓存的TTL
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 存储缓存 - 30分钟
        cacheConfigurations.put("storage", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // 用户会话缓存 - 2小时
        cacheConfigurations.put("user-session", defaultConfig.entryTtl(Duration.ofHours(2)));
        
        // 热点数据缓存 - 5分钟
        cacheConfigurations.put("hot-data", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // 统计数据缓存 - 1分钟
        cacheConfigurations.put("stats", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // 元数据缓存 - 10分钟
        cacheConfigurations.put("metadata", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
} 