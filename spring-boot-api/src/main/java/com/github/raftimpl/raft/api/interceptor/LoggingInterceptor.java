package com.github.raftimpl.raft.api.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Component
public class LoggingInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String START_TIME_KEY = "startTime";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 生成请求ID
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MDC.put(REQUEST_ID_KEY, requestId);
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_KEY, startTime);
        
        // 记录请求信息
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        
        logger.info("API请求开始 - Method: {}, URI: {}, Query: {}, IP: {}, UserAgent: {}", 
                method, uri, queryString, clientIp, userAgent);
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        try {
            // 计算处理时间
            Long startTime = (Long) request.getAttribute(START_TIME_KEY);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            
            // 记录响应信息
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            String clientIp = getClientIpAddress(request);
            
            if (ex != null) {
                logger.error("API请求异常 - Method: {}, URI: {}, Status: {}, Duration: {}ms, IP: {}, Error: {}", 
                        method, uri, status, duration, clientIp, ex.getMessage(), ex);
            } else {
                logger.info("API请求完成 - Method: {}, URI: {}, Status: {}, Duration: {}ms, IP: {}", 
                        method, uri, status, duration, clientIp);
            }
        } finally {
            // 清理MDC
            MDC.clear();
        }
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
