package com.example.aihub.module.prompttemplate;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.dto.PromptTemplateSaveDTO;
import com.example.aihub.infrastructure.dto.PublicCommentSaveDTO;
import com.example.aihub.infrastructure.service.PublicContentInteractionService;
import com.example.aihub.infrastructure.service.PromptTemplateService;
import com.example.aihub.infrastructure.vo.PublicCommentVO;
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
    private final PublicContentInteractionService interactionService;

    @GetMapping
    public ApiResult<PageResult<PromptTemplateVO>> list(@RequestParam(name = "tag", required = false) String tag,
                                                        @RequestParam(name = "search", required = false) String search,
                                                        @RequestParam(name = "sort", required = false, defaultValue = "newest") String sort,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "12") long pageSize) {
        return ApiResult.ok(templateService.page(
                tag,
                search,
                sort,
                StpUtil.getLoginIdAsLong(),
                PagingUtil.normalizePage(page),
                PagingUtil.clampPageSize(pageSize, 12)
        ));
    }

    @GetMapping("/{id}")
    public ApiResult<PromptTemplateVO> get(@PathVariable Long id) {
        return ApiResult.ok(templateService.get(id, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/tags")
    public ApiResult<List<String>> tags() {
        return ApiResult.ok(templateService.tags());
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

    @PostMapping("/{id}/like")
    public ApiResult<Integer> toggleLike(@PathVariable Long id) {
        return ApiResult.ok(interactionService.toggleLike(
                PublicContentInteractionService.RESOURCE_PROMPT_TEMPLATE,
                id,
                StpUtil.getLoginIdAsLong()
        ));
    }

    @GetMapping("/{id}/comments")
    public ApiResult<List<PublicCommentVO>> listComments(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "200") int limit) {
        return ApiResult.ok(interactionService.listComments(
                PublicContentInteractionService.RESOURCE_PROMPT_TEMPLATE,
                id,
                PagingUtil.clampLimit(limit, 200, 200)
        ));
    }

    @PostMapping("/{id}/comments")
    public ApiResult<PublicCommentVO> createComment(@PathVariable Long id, @Valid @RequestBody PublicCommentSaveDTO dto) {
        return ApiResult.ok(interactionService.createComment(
                PublicContentInteractionService.RESOURCE_PROMPT_TEMPLATE,
                id,
                StpUtil.getLoginIdAsLong(),
                dto
        ));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ApiResult<Integer> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        return ApiResult.ok(interactionService.deleteComment(
                PublicContentInteractionService.RESOURCE_PROMPT_TEMPLATE,
                id,
                commentId,
                StpUtil.getLoginIdAsLong()
        ));
    }
}
