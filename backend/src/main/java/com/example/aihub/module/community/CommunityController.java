package com.example.aihub.module.community;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.CommunityPostDTO;
import com.example.aihub.infrastructure.dto.PublicCommentSaveDTO;
import com.example.aihub.infrastructure.service.CommunityService;
import com.example.aihub.infrastructure.service.PublicContentInteractionService;
import com.example.aihub.infrastructure.vo.CommunityPostVO;
import com.example.aihub.infrastructure.vo.PublicCommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService communityService;
    private final PublicContentInteractionService interactionService;

    @GetMapping("/posts")
    public ApiResult<com.example.aihub.common.result.PageResult<CommunityPostVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return ApiResult.ok(communityService.page(category, search, sort, userId, page, pageSize));
    }

    @GetMapping("/posts/{id}")
    public ApiResult<CommunityPostVO> get(@PathVariable Long id) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return ApiResult.ok(communityService.get(id, userId));
    }

    @PostMapping("/posts")
    @SaCheckLogin
    public ApiResult<CommunityPostVO> create(@Valid @RequestBody CommunityPostDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        String username = String.valueOf(StpUtil.getLoginId());
        return ApiResult.ok(communityService.create(dto, userId, username));
    }

    @PutMapping("/posts/{id}")
    @SaCheckLogin
    public ApiResult<CommunityPostVO> update(@PathVariable Long id, @Valid @RequestBody CommunityPostDTO dto) {
        return ApiResult.ok(communityService.update(id, dto, StpUtil.getLoginIdAsLong()));
    }

    @DeleteMapping("/posts/{id}")
    @SaCheckLogin
    public ApiResult<Void> delete(@PathVariable Long id) {
        communityService.delete(id, StpUtil.getLoginIdAsLong());
        return ApiResult.ok();
    }

    @PostMapping("/posts/{id}/like")
    @SaCheckLogin
    public ApiResult<Integer> toggleLike(@PathVariable Long id) {
        return ApiResult.ok(interactionService.toggleLike(
                PublicContentInteractionService.RESOURCE_COMMUNITY_POST,
                id,
                StpUtil.getLoginIdAsLong()
        ));
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResult<List<PublicCommentVO>> listComments(@PathVariable Long id) {
        return ApiResult.ok(interactionService.listComments(
                PublicContentInteractionService.RESOURCE_COMMUNITY_POST,
                id
        ));
    }

    @PostMapping("/posts/{id}/comments")
    @SaCheckLogin
    public ApiResult<PublicCommentVO> createComment(@PathVariable Long id, @Valid @RequestBody PublicCommentSaveDTO dto) {
        return ApiResult.ok(interactionService.createComment(
                PublicContentInteractionService.RESOURCE_COMMUNITY_POST,
                id,
                StpUtil.getLoginIdAsLong(),
                dto
        ));
    }

    @DeleteMapping("/posts/{id}/comments/{commentId}")
    @SaCheckLogin
    public ApiResult<Integer> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        return ApiResult.ok(interactionService.deleteComment(
                PublicContentInteractionService.RESOURCE_COMMUNITY_POST,
                id,
                commentId,
                StpUtil.getLoginIdAsLong()
        ));
    }

    @GetMapping("/categories")
    public ApiResult<List<String>> categories() {
        return ApiResult.ok(communityService.categories());
    }
}
