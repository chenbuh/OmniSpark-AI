package com.example.aihub.common.audit;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;

public final class AuditActionSupport {
    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String MODULE_PACKAGE_PREFIX = "com.example.aihub.module.";

    private AuditActionSupport() {
    }

    public static boolean shouldLog(Class<?> beanType, Method method) {
        if (beanType == null || method == null) {
            return false;
        }
        Package beanPackage = beanType.getPackage();
        if (beanPackage == null || !beanPackage.getName().startsWith(MODULE_PACKAGE_PREFIX)) {
            return false;
        }
        if (!beanType.getSimpleName().endsWith(CONTROLLER_SUFFIX)) {
            return false;
        }
        return hasWriteHttpMethod(method);
    }

    public static String resolveAction(Class<?> beanType, Method method) {
        if (!shouldLog(beanType, method)) {
            return null;
        }
        return beanType.getSimpleName().replace(CONTROLLER_SUFFIX, "") + "_" + method.getName();
    }

    private static boolean hasWriteHttpMethod(Method method) {
        if (AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class)) {
            return true;
        }
        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (requestMapping == null || requestMapping.method().length == 0) {
            return false;
        }
        for (RequestMethod requestMethod : requestMapping.method()) {
            if (requestMethod != RequestMethod.GET && requestMethod != RequestMethod.HEAD && requestMethod != RequestMethod.OPTIONS) {
                return true;
            }
        }
        return false;
    }
}
