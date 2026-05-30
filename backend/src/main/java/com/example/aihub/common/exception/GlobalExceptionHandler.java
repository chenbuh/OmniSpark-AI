package com.example.aihub.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.example.aihub.common.result.ApiResult;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusiness(BusinessException ex) {
        return ApiResult.fail(sanitizeMessage(ex.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResult<Void> handleNotLogin(NotLoginException ex) {
        return ApiResult.fail(401, "登录已失效，请重新登录");
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResult<Void> handleNoPermission(Exception ex) {
        return ApiResult.fail(403, "无权限访问该资源");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return ApiResult.fail(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<Void> handleConstraint(ConstraintViolationException ex) {
        return ApiResult.fail(sanitizeMessage(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleAny(Exception ex) {
        // 非预期异常的原始 message 可能含 SQL/IO/堆栈细节，仅记日志，对外返回通用文案
        log.error("未处理的异常", ex);
        return ApiResult.fail("系统处理失败，请稍后重试");
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "系统处理失败，请稍后重试";
        }
        if (looksLikeHtml(message)) {
            String normalized = message.toLowerCase(Locale.ROOT);
            if (normalized.contains("<title>nl-api</title>")) {
                return "远程模型服务返回了 nl-api 管理页 HTML，请检查 Base URL 是否填写成了站点地址而不是 OpenAI 接口地址";
            }
            return "远程接口返回了 HTML 页面，请确认请求目标是 API 接口而不是网站首页";
        }
        return message;
    }

    private boolean looksLikeHtml(String message) {
        String trimmed = message.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("<!doctype html")
                || trimmed.startsWith("<html")
                || trimmed.startsWith("<body")
                || trimmed.contains("<head>")
                || trimmed.contains("<title>");
    }
}
