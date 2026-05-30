package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.service.StatsService;
import com.example.aihub.infrastructure.vo.StatsDashboardVO;
import com.example.aihub.infrastructure.vo.StatsOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
@SaCheckLogin
public class StatsController {
    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResult<StatsOverviewVO> overview(@RequestParam(name = "projectId", required = false) Long projectId) {
        return ApiResult.ok(statsService.overview(projectId));
    }

    @GetMapping("/dashboard")
    public ApiResult<StatsDashboardVO> dashboard(@RequestParam(name = "projectId", required = false) Long projectId) {
        return ApiResult.ok(statsService.dashboard(projectId));
    }
}
