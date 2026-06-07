package com.example.aihub.module.stylecard;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.PublicCommentSaveDTO;
import com.example.aihub.infrastructure.dto.StyleCardSaveDTO;
import com.example.aihub.infrastructure.service.PublicContentInteractionService;
import com.example.aihub.infrastructure.service.StyleCardService;
import com.example.aihub.infrastructure.vo.PublicCommentVO;
import com.example.aihub.infrastructure.vo.StyleCardVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/style-cards")
@SaCheckLogin
public class StyleCardController {
    private final StyleCardService styleCardService;
    private final PublicContentInteractionService interactionService;

    @GetMapping
    public ApiResult<PageResult<StyleCardVO>> list(@RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String search,
                                                   @RequestParam(required = false, defaultValue = "newest") String sort,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "12") long pageSize) {
        return ApiResult.ok(styleCardService.page(
                type,
                search,
                sort,
                StpUtil.getLoginIdAsLong(),
                PagingUtil.normalizePage(page),
                PagingUtil.clampPageSize(pageSize, 12)
        ));
    }

    @GetMapping("/{id}")
    public ApiResult<StyleCardVO> get(@PathVariable Long id) {
        return ApiResult.ok(styleCardService.get(id, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/tags")
    public ApiResult<List<String>> tags() {
        return ApiResult.ok(styleCardService.tags());
    }

    @PostMapping
    public ApiResult<StyleCardVO> create(@Valid @RequestBody StyleCardSaveDTO dto) {
        return ApiResult.ok(styleCardService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResult<StyleCardVO> update(@PathVariable Long id, @Valid @RequestBody StyleCardSaveDTO dto) {
        return ApiResult.ok(styleCardService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        styleCardService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/like")
    public ApiResult<Integer> toggleLike(@PathVariable Long id) {
        return ApiResult.ok(interactionService.toggleLike(
                PublicContentInteractionService.RESOURCE_STYLE_CARD,
                id,
                StpUtil.getLoginIdAsLong()
        ));
    }

    @GetMapping("/{id}/comments")
    public ApiResult<List<PublicCommentVO>> listComments(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "200") int limit) {
        return ApiResult.ok(interactionService.listComments(
                PublicContentInteractionService.RESOURCE_STYLE_CARD,
                id,
                PagingUtil.clampLimit(limit, 200, 200)
        ));
    }

    @PostMapping("/{id}/comments")
    public ApiResult<PublicCommentVO> createComment(@PathVariable Long id, @Valid @RequestBody PublicCommentSaveDTO dto) {
        return ApiResult.ok(interactionService.createComment(
                PublicContentInteractionService.RESOURCE_STYLE_CARD,
                id,
                StpUtil.getLoginIdAsLong(),
                dto
        ));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ApiResult<Integer> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        return ApiResult.ok(interactionService.deleteComment(
                PublicContentInteractionService.RESOURCE_STYLE_CARD,
                id,
                commentId,
                StpUtil.getLoginIdAsLong()
        ));
    }
}
