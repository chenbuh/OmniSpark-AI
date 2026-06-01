package com.example.aihub.module.auth;

import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.CaptchaVerifyDTO;
import com.example.aihub.infrastructure.service.CaptchaService;
import com.example.aihub.infrastructure.vo.CaptchaVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 自研多形态验证码接口（匿名可访问）。
 * 生成接口本身也限流，防止有人狂刷生成图消耗服务端 CPU。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/captcha")
public class CaptchaController {
    private final CaptchaService captchaService;

    @GetMapping("/generate")
    @RateLimit(count = 30, seconds = 60, dimension = RateLimit.Dimension.IP, message = "请求过于频繁，请稍后再试")
    public ApiResult<CaptchaVO> generate(HttpServletRequest request) {
        return ApiResult.ok(captchaService.generate(clientIp(request)));
    }

    @PostMapping("/verify")
    @RateLimit(count = 30, seconds = 60, dimension = RateLimit.Dimension.IP, message = "请求过于频繁，请稍后再试")
    public ApiResult<Map<String, String>> verify(@Valid @RequestBody CaptchaVerifyDTO dto, HttpServletRequest request) {
        String ticket = captchaService.verify(dto, clientIp(request));
        return ApiResult.ok(Map.of("ticket", ticket));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
