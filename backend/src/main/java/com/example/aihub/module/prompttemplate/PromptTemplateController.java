package com.example.aihub.module.prompttemplate;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.PromptTemplateSaveDTO;
import com.example.aihub.infrastructure.service.PromptTemplateService;
import com.example.aihub.infrastructure.vo.PromptTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/prompt-templates")
@SaCheckLogin
public class PromptTemplateController {
    private final PromptTemplateService templateService;

    @GetMapping
    public ApiResult<List<PromptTemplateVO>> list(@RequestParam(name = "projectId", required = false) Long projectId) {
        return ApiResult.ok(templateService.list(projectId));
    }

    @PostMapping
    public ApiResult<PromptTemplateVO> create(@Valid @RequestBody PromptTemplateSaveDTO dto) {
        return ApiResult.ok(templateService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResult<PromptTemplateVO> update(@PathVariable Long id, @Valid @RequestBody PromptTemplateSaveDTO dto) {
        return ApiResult.ok(templateService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ApiResult.ok();
    }
}
