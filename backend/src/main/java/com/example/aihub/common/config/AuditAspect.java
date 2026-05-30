package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.infrastructure.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    private final AuditLogService auditLogService;

    @Pointcut("execution(* com.example.aihub.module..*Controller.*(..))")
    public void controllerMethods() {}

    @AfterReturning(pointcut = "controllerMethods()", returning = "result")
    public void logAfterController(JoinPoint joinPoint, Object result) {
        try {
            // 只记录已登录用户的操作
            if (!StpUtil.isLogin()) return;

            Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
            String username = "";
            try {
                username = StpUtil.getLoginIdAsString();
            } catch (Exception ignored) {}

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String className = method.getDeclaringClass().getSimpleName();
            String methodName = method.getName();

            // 构建操作名：Controller名_方法名
            String action = className.replace("Controller", "") + "_" + methodName;

            // 跳过查询类操作
            if (methodName.startsWith("get") || methodName.startsWith("list")
                    || methodName.startsWith("page") || methodName.startsWith("me")) {
                return;
            }

            // 获取请求 IP
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ip = request != null ? request.getRemoteAddr() : null;

            // 构建详情
            StringBuilder detail = new StringBuilder();
            detail.append(methodName).append(" on ").append(className);
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                detail.append(" with ").append(args.length).append(" params");
            }

            auditLogService.log(userId, username, action, null, null, detail.toString(), ip);
        } catch (Exception ignored) {
            // 审计日志不能影响主业务流程
        }
    }
}
