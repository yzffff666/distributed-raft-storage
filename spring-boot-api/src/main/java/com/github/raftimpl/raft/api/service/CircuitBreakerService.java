package com.github.raftimpl.raft.api.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class CircuitBreakerService {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerService.class);
    
    /**
     * 使用熔断器包装Raft服务调用
     */
    @CircuitBreaker(name = "raft-service", fallbackMethod = "fallbackMethod")
    public <T> T executeWithCircuitBreaker(Supplier<T> supplier) {
        return supplier.get();
    }
    
    /**
     * 熔断器降级方法
     */
    public <T> T fallbackMethod(Supplier<T> supplier, Exception ex) {
        logger.error("Circuit breaker activated, falling back to default response", ex);
        return null;
    }
    
    /**
     * 使用熔断器包装存储操作
     */
    @CircuitBreaker(name = "raft-service", fallbackMethod = "fallbackStorageMethod")
    public String executeStorageOperation(Supplier<String> operation) {
        return operation.get();
    }
    
    /**
     * 存储操作降级方法
     */
    public String fallbackStorageMethod(Supplier<String> operation, Exception ex) {
        logger.error("Storage operation failed, circuit breaker activated", ex);
        return "服务暂时不可用，请稍后重试";
    }
}
