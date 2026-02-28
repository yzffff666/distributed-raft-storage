package com.github.raftimpl.raft.api.interceptor;

import brave.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 链路追踪拦截器
 * 自动为HTTP请求添加追踪信息和MDC日志关联
 */
@Component
public class TracingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TracingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // 获取当前追踪信息
            brave.Span currentSpan = getCurrentSpan();
            
            if (currentSpan != null) {
                // 添加请求信息到span
                currentSpan.tag("http.method", request.getMethod());
                currentSpan.tag("http.url", request.getRequestURL().toString());
                currentSpan.tag("http.user_agent", request.getHeader("User-Agent"));
                
                // 获取客户端IP
                String clientIp = getClientIpAddress(request);
                currentSpan.tag("client.ip", clientIp);
                
                // 获取用户信息（如果存在）
                String userId = getUserId(request);
                if (userId != null) {
                    currentSpan.tag("user.id", userId);
                }
                
                // 添加追踪ID到MDC，用于日志关联
                String traceId = currentSpan.context().traceIdString();
                String spanId = currentSpan.context().spanIdString();
                MDC.put("traceId", traceId);
                MDC.put("spanId", spanId);
                
                logger.debug("开始处理请求: {} {}, TraceId: {}, SpanId: {}", 
                    request.getMethod(), request.getRequestURI(), traceId, spanId);
            }
            
        } catch (Exception e) {
            logger.warn("链路追踪预处理失败", e);
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) {
        try {
            brave.Span currentSpan = getCurrentSpan();
            
            if (currentSpan != null) {
                // 添加响应状态码
                currentSpan.tag("http.status_code", String.valueOf(response.getStatus()));
                
                // 如果有异常，记录错误信息
                if (ex != null) {
                    currentSpan.tag("error", "true");
                    currentSpan.tag("error.message", ex.getMessage());
                    currentSpan.tag("error.type", ex.getClass().getSimpleName());
                    logger.error("请求处理异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
                }
                
                logger.debug("完成处理请求: {} {}, 状态码: {}", 
                    request.getMethod(), request.getRequestURI(), response.getStatus());
            }
            
        } catch (Exception e) {
            logger.warn("链路追踪后处理失败", e);
        } finally {
            // 清理MDC
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    /**
     * 获取当前Span
     */
    private brave.Span getCurrentSpan() {
        try {
            Tracing tracing = Tracing.current();
            if (tracing != null && tracing.tracer() != null) {
                return tracing.tracer().currentSpan();
            }
        } catch (Exception e) {
            logger.debug("获取当前Span失败", e);
        }
        return null;
    }

    /**
     * 获取客户端IP地址
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

    /**
     * 获取用户ID（从JWT token或session中）
     */
    private String getUserId(HttpServletRequest request) {
        try {
            // 尝试从JWT token中获取用户ID
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // 这里可以解析JWT token获取用户ID
                // 为了简化，这里返回null
                return null;
            }
            
            // 尝试从session中获取用户ID
            Object userId = request.getSession(false) != null ? 
                request.getSession().getAttribute("userId") : null;
            return userId != null ? userId.toString() : null;
            
        } catch (Exception e) {
            logger.debug("获取用户ID失败", e);
            return null;
        }
    }
} 