package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.service.AccessLogService;
import com.example.aihub.infrastructure.vo.AccessLogAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/access-logs")
@SaCheckLogin
@SaCheckRole("admin")
public class AccessLogController {
    private final AccessLogService accessLogService;

    @GetMapping
    @RateLimit(count = 60, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "访问日志查询过于频繁，请稍后再试")
    public ApiResult<PageResult<AccessLogAdminVO>> page(@RequestParam(required = false) String clientIp,
                                                        @RequestParam(required = false) Long userId,
                                                        @RequestParam(required = false) Long apiKeyId,
                                                        @RequestParam(required = false) String path,
                                                        @RequestParam(required = false) Integer statusCode,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(accessLogService.page(clientIp, userId, apiKeyId, path, statusCode, page, PagingUtil.clampPageSize(pageSize, 100)));
    }

    @GetMapping("/summary")
    @RateLimit(count = 60, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "访问日志聚合查询过于频繁，请稍后再试")
    public ApiResult<Map<String, Object>> summary(@RequestParam(defaultValue = "60") int minutes) {
        return ApiResult.ok(accessLogService.summary(minutes));
    }
}
