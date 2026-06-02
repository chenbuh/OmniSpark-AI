package com.example.aihub.module.project;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.security.CanaryTokenService;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.service.ProjectService;
import com.example.aihub.infrastructure.vo.ProjectExportVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
@SaCheckLogin
public class ExportController {
    private final ProjectService projectService;
    private final CanaryTokenService canaryTokenService;

    @PostMapping("/{id}/export")
    @RateLimit(count = 10, seconds = 3600, dimension = RateLimit.Dimension.IP, message = "项目导出过于频繁，请稍后再试")
    @RateLimit(count = 5, seconds = 3600, dimension = RateLimit.Dimension.USER_API, message = "项目导出过于频繁，请稍后再试")
    public ApiResult<ProjectExportVO> export(@PathVariable Long id, HttpServletRequest request) {
        ProjectExportVO vo = projectService.exportProject(id);
        vo.setCanaryToken(canaryTokenService.create("project_export", String.valueOf(id), request));
        return ApiResult.ok(vo);
    }

    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importProject(@RequestBody ProjectExportVO data) {
        Long newProjectId = projectService.importProject(data);
        return ApiResult.ok(Map.of("projectId", newProjectId));
    }
}
