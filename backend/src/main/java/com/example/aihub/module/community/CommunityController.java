package com.example.aihub.module.community;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.CommunityPostDTO;
import com.example.aihub.infrastructure.service.CommunityService;
import com.example.aihub.infrastructure.vo.CommunityPostVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService communityService;

    @GetMapping("/posts")
    public ApiResult<List<CommunityPostVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return ApiResult.ok(communityService.list(category, search, userId));
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

    @DeleteMapping("/posts/{id}")
    @SaCheckLogin
    public ApiResult<Void> delete(@PathVariable Long id) {
        communityService.delete(id, StpUtil.getLoginIdAsLong());
        return ApiResult.ok();
    }

    @PostMapping("/posts/{id}/like")
    @SaCheckLogin
    public ApiResult<Integer> toggleLike(@PathVariable Long id) {
        return ApiResult.ok(communityService.toggleLike(id, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/categories")
    public ApiResult<List<String>> categories() {
        return ApiResult.ok(communityService.categories());
    }
}
