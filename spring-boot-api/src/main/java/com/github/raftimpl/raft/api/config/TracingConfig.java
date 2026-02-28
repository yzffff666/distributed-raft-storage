package com.github.raftimpl.raft.api.config;

import brave.sampler.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分布式链路追踪配置
 * 配置Sleuth和Zipkin集成
 */
@Configuration
public class TracingConfig {

    @Value("${spring.sleuth.sampler.probability:1.0}")
    private float samplingRate;

    /**
     * 配置采样器
     * 控制链路追踪的采样率
     */
    @Bean
    public Sampler alwaysSampler() {
        return Sampler.create(samplingRate);
    }

    /**
     * 添加自定义标签工具
     */
    @Bean
    public TracingUtils tracingUtils() {
        return new TracingUtils();
    }

    /**
     * 链路追踪工具类
     */
    public static class TracingUtils {
        
        /**
         * 添加用户标签
         */
        public void tagUser(String userId) {
            brave.Tracing tracing = brave.Tracing.current();
            if (tracing != null && tracing.tracer().currentSpan() != null) {
                tracing.tracer().currentSpan().tag("user.id", userId);
            }
        }

        /**
         * 添加业务标签
         */
        public void tagBusiness(String operation, String resource) {
            brave.Tracing tracing = brave.Tracing.current();
            if (tracing != null && tracing.tracer().currentSpan() != null) {
                tracing.tracer().currentSpan()
                    .tag("business.operation", operation)
                    .tag("business.resource", resource);
            }
        }

        /**
         * 添加错误标签
         */
        public void tagError(String errorType, String errorMessage) {
            brave.Tracing tracing = brave.Tracing.current();
            if (tracing != null && tracing.tracer().currentSpan() != null) {
                tracing.tracer().currentSpan()
                    .tag("error", "true")
                    .tag("error.type", errorType)
                    .tag("error.message", errorMessage);
            }
        }
    }
} 