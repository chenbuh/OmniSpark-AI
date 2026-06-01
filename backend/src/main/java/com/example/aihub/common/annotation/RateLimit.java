package com.example.aihub.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：基于 Redis 固定窗口对「单位时间内某主体的请求次数」计数，超过阈值返回 429。
 *
 * <p>用法示例：
 * <pre>
 *   // 登录：每个 IP 每分钟最多 10 次
 *   {@code @RateLimit(count = 10, seconds = 60, dimension = RateLimit.Dimension.IP)}
 *
 *   // 生成：每个用户对该接口每分钟最多 10 次
 *   {@code @RateLimit(count = 10, seconds = 60, dimension = RateLimit.Dimension.USER_API)}
 * </pre>
 *
 * <p>可在同一方法上叠加多个 {@code @RateLimit}（如同时限"每分钟"和"每天"），见 {@link RateLimits}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Repeatable(RateLimits.class)
public @interface RateLimit {

    /** 窗口内允许的最大请求次数。 */
    int count();

    /** 窗口大小（秒）。 */
    int seconds();

    /** 限流主体维度。 */
    Dimension dimension() default Dimension.USER;

    /** 超限时返回给前端的提示文案。 */
    String message() default "操作过于频繁，请稍后再试";

    /**
     * 限流主体维度：
     * <ul>
     *   <li>{@link #IP} —— 按来源 IP，适用于登录/注册等匿名接口</li>
     *   <li>{@link #USER} —— 按登录用户，适用于通用已登录接口</li>
     *   <li>{@link #USER_API} —— 按"登录用户 + 具体接口"，适用于生成等高成本接口</li>
     * </ul>
     */
    enum Dimension {
        IP,
        USER,
        USER_API
    }
}
