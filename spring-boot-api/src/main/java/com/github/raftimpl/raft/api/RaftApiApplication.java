package com.github.raftimpl.raft.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 分布式存储系统RESTful API服务
 * 
 */
@SpringBootApplication(exclude = {
    org.redisson.spring.starter.RedissonAutoConfiguration.class
})
@EnableCaching
@EnableScheduling
public class RaftApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaftApiApplication.class, args);
    }
} 