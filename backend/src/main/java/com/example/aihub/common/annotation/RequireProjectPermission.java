package com.example.aihub.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 项目权限注解。
 * 在 Controller 方法上标注，自动校验当前用户对指定项目是否有要求的权限。
 *
 * 用法：
 * &#64;RequireProjectPermission(value = "admin")
 * public ApiResult<?> update(@RequestParam Long projectId, ...) { ... }
 *
 * 参数 projectId 的来源：
 * 1. 方法参数中名为 projectId 的参数
 * 2. 请求体 DTO 中的 projectId 字段
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireProjectPermission {
    String value() default "view";
}
