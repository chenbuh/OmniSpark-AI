package com.example.aihub.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link RateLimit} 的容器注解，使同一方法可叠加多条限流规则
 * （例如同时限制"每分钟 10 次"与"每天 200 次"）。
 * 通常无需直接使用，重复书写 {@code @RateLimit} 即可由编译器自动包装。
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimits {
    RateLimit[] value();
}
