package com.example.aihub.module.project;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.service.ProjectService;
import com.example.aihub.infrastructure.vo.ProjectExportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
@SaCheckLogin
public class ExportController {
    private final ProjectService projectService;

    @PostMapping("/{id}/export")
    public ApiResult<ProjectExportVO> export(@PathVariable Long id) {
        return ApiResult.ok(projectService.exportProject(id));
    }

    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importProject(@RequestBody ProjectExportVO data) {
        Long newProjectId = projectService.importProject(data);
        return ApiResult.ok(Map.of("projectId", newProjectId));
    }
}
