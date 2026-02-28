package com.github.raftimpl.raft.api.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.raftimpl.raft.api.dto.ApiResponse;
import com.github.raftimpl.raft.api.service.MonitoringService;
import com.github.raftimpl.raft.api.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final int TOO_MANY_REQUESTS = 429; // HTTP 429状态码
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Autowired
    private MonitoringService monitoringService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIpAddress(request);
        String endpoint = request.getRequestURI();
        String rateLimitKey = rateLimitService.getRateLimitKey(clientIp, endpoint);
        
        if (!rateLimitService.isAllowed(rateLimitKey, 1)) {
            logger.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, endpoint);
            
            // 记录限流事件到监控
            monitoringService.recordRateLimitExceeded(endpoint);
            
            response.setStatus(TOO_MANY_REQUESTS);
            response.setContentType("application/json;charset=UTF-8");
            
            ApiResponse<Object> apiResponse = ApiResponse.error("请求过于频繁，请稍后再试");
            String jsonResponse = objectMapper.writeValueAsString(apiResponse);
            response.getWriter().write(jsonResponse);
            
            return false;
        }
        
        return true;
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
