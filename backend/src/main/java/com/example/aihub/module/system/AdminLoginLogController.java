package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.service.AdminLoginLogService;
import com.example.aihub.infrastructure.vo.LoginLogAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/login-logs")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminLoginLogController {
    private final AdminLoginLogService adminLoginLogService;

    @GetMapping
    public ApiResult<PageResult<LoginLogAdminVO>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(adminLoginLogService.page(userId, username, ip, PagingUtil.normalizePage(page), PagingUtil.clampPageSize(pageSize, 100)));
    }
}
