package com.example.aihub.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final MaintenanceInterceptor maintenanceInterceptor;
    private final ApiSignInterceptor apiSignInterceptor;
    private final AntiCrawlerRiskInterceptor antiCrawlerRiskInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(maintenanceInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(apiSignInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(antiCrawlerRiskInterceptor).addPathPatterns("/api/**");
        // 限流拦截器：放在签名与风险校验之后、业务与鉴权之前，对带 @RateLimit 的接口按维度计数拦截。
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
        // 注册 Sa-Token 注解拦截器，使 @SaCheckLogin / @SaCheckRole 等注解生效。
        // Spring Boot 3 的 sa-token starter 不会自动注册该拦截器，必须手动注册。
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/captcha/generate",
                        "/api/auth/captcha/verify"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
