package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/login-logs")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminLoginLogController {
    private final LoginLogMapper loginLogMapper;

    @GetMapping
    public ApiResult<PageResult<LoginLog>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        var wrapper = new LambdaQueryWrapper<LoginLog>();
        if (userId != null) wrapper.eq(LoginLog::getUserId, userId);
        wrapper.orderByDesc(LoginLog::getId);
        var p = loginLogMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        return ApiResult.ok(new PageResult<>(p.getTotal(), p.getPages(), p.getRecords()));
    }
}
