package com.example.aihub.module.audit;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.infrastructure.service.AuditLogService;
import com.example.aihub.infrastructure.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/audit-logs")
@SaCheckLogin
public class AuditController {
    private final AuditLogService auditLogService;

    /** 全量审计日志查询，仅管理员可用。普通用户请用 /my 查看本人记录。 */
    @GetMapping
    @SaCheckRole("admin")
    public ApiResult<PageResult<AuditLogVO>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(auditLogService.page(action, userId, page, size));
    }

    @GetMapping("/my")
    public ApiResult<PageResult<AuditLogVO>> myLogs(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        return ApiResult.ok(auditLogService.page(action, userId, page, size));
    }
}
