package com.github.raftimpl.raft.api.config;

import com.github.raftimpl.raft.api.interceptor.LoggingInterceptor;
import com.github.raftimpl.raft.api.interceptor.MonitoringInterceptor;
import com.github.raftimpl.raft.api.interceptor.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    
    @Autowired
    private LoggingInterceptor loggingInterceptor;
    
    @Autowired
    private MonitoringInterceptor monitoringInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 监控拦截器 - 第一执行，记录所有请求
        registry.addInterceptor(monitoringInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(0);
        
        // 日志拦截器 - 第二执行，记录详细日志
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(1);
                
        // 限流拦截器 - 最后执行，进行限流检查
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/**"
                )
                .order(2);
    }
}
