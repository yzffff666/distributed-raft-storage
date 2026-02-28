package com.github.raftimpl.raft.api.interceptor;

import com.github.raftimpl.raft.api.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class MonitoringInterceptor implements HandlerInterceptor {
    
    private static final String TIMER_SAMPLE_KEY = "timer_sample";
    
    @Autowired
    private MonitoringService monitoringService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 记录API请求
        String method = request.getMethod();
        String endpoint = request.getRequestURI();
        monitoringService.recordApiRequest(method, endpoint);
        
        // 增加活跃连接数
        monitoringService.incrementActiveConnections();
        
        // 开始计时
        Object sample = monitoringService.startApiTimer();
        request.setAttribute(TIMER_SAMPLE_KEY, sample);
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        try {
            // 记录响应时间
            Object sample = request.getAttribute(TIMER_SAMPLE_KEY);
            if (sample != null) {
                String method = request.getMethod();
                String endpoint = request.getRequestURI();
                monitoringService.recordApiDuration(sample, method, endpoint);
            }
            
            // 记录错误
            if (ex != null || response.getStatus() >= 400) {
                String method = request.getMethod();
                String endpoint = request.getRequestURI();
                String errorType = ex != null ? ex.getClass().getSimpleName() : "HTTP_" + response.getStatus();
                monitoringService.recordApiError(method, endpoint, errorType);
            }
        } finally {
            // 减少活跃连接数
            monitoringService.decrementActiveConnections();
        }
    }
}
