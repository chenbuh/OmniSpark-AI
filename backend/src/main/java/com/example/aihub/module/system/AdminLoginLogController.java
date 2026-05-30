package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/login-logs")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminLoginLogController {
    private final LoginLogMapper loginLogMapper;

    @GetMapping
    public ApiResult<List<LoginLog>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "100") int limit) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LoginLog>();
        if (userId != null) wrapper.eq(LoginLog::getUserId, userId);
        wrapper.orderByDesc(LoginLog::getId).last("LIMIT " + limit);
        return ApiResult.ok(loginLogMapper.selectList(wrapper));
    }
}
