package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.service.AdminCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/cleanup")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminCleanupController {
    private final AdminCleanupService adminCleanupService;

    @GetMapping("/preview")
    public ApiResult<Map<String, Object>> preview(@RequestParam(defaultValue = "30") int daysOld) {
        return ApiResult.ok(adminCleanupService.preview(daysOld));
    }

    @DeleteMapping("/execute")
    public ApiResult<Map<String, Object>> execute(@RequestParam(defaultValue = "30") int daysOld) {
        return ApiResult.ok(adminCleanupService.execute(daysOld));
    }
}
