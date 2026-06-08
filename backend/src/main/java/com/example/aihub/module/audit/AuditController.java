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
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(auditLogService.page(action, userId, username, ip, page, size));
    }

    @GetMapping("/my")
    public ApiResult<PageResult<AuditLogVO>> myLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        return ApiResult.ok(auditLogService.page(action, userId, null, ip, page, size));
    }

    @GetMapping("/actions")
    public ApiResult<java.util.List<String>> actions() {
        return ApiResult.ok(auditLogService.actions());
    }

    @GetMapping("/cleanup-preview")
    @SaCheckRole("admin")
    public ApiResult<Long> cleanupPreview(@RequestParam(defaultValue = "30") int daysOld) {
        if (daysOld < 7) {
            return ApiResult.fail("保留天数不能小于 7 天，以防误删近期审计记录");
        }
        return ApiResult.ok(auditLogService.countOlderThan(daysOld));
    }

    /** 清理 N 天前的审计日志,仅管理员可用。下限 7 天,防止误删近期合规记录。 */
    @DeleteMapping
    @SaCheckRole("admin")
    public ApiResult<Long> cleanup(@RequestParam(defaultValue = "30") int daysOld) {
        if (daysOld < 7) {
            return ApiResult.fail("保留天数不能小于 7 天，以防误删近期审计记录");
        }
        return ApiResult.ok(auditLogService.deleteOlderThan(daysOld));
    }
}
