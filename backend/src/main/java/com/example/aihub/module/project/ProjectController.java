package com.example.aihub.module.project;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.annotation.RequireProjectPermission;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.ProjectSaveDTO;
import com.example.aihub.infrastructure.service.ProjectService;
import com.example.aihub.infrastructure.vo.ProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
@SaCheckLogin
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public ApiResult<List<ProjectVO>> list() {
        return ApiResult.ok(projectService.listMine());
    }

    @PostMapping
    public ApiResult<ProjectVO> create(@Valid @RequestBody ProjectSaveDTO dto) {
        return ApiResult.ok(projectService.create(dto));
    }

    @PutMapping("/{id}")
    @RequireProjectPermission("edit")
    public ApiResult<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectSaveDTO dto) {
        return ApiResult.ok(projectService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResult.ok();
    }
}
