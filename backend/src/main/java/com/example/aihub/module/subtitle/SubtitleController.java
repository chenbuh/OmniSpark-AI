package com.example.aihub.module.subtitle;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.dto.SubtitleGenerateDTO;
import com.example.aihub.infrastructure.dto.SubtitleUpdateDTO;
import com.example.aihub.infrastructure.service.SubtitleService;
import com.example.aihub.infrastructure.vo.SubtitleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/video/subtitles")
@SaCheckLogin
public class SubtitleController {
    private final SubtitleService subtitleService;

    @GetMapping("/{assetId}")
    public ApiResult<List<SubtitleVO>> list(@PathVariable Long assetId,
                                            @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(subtitleService.listByAsset(assetId, PagingUtil.clampLimit(limit, 100, 100)));
    }

    @GetMapping("/{assetId}/page")
    public ApiResult<PageResult<SubtitleVO>> page(@PathVariable Long assetId,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "100") long pageSize) {
        return ApiResult.ok(subtitleService.pageByAsset(assetId, page, pageSize));
    }

    @PostMapping("/generate")
    public ApiResult<SubtitleVO> generate(@Valid @RequestBody SubtitleGenerateDTO dto) {
        return ApiResult.ok(subtitleService.generate(dto));
    }

    @PutMapping
    public ApiResult<SubtitleVO> update(@Valid @RequestBody SubtitleUpdateDTO dto) {
        return ApiResult.ok(subtitleService.update(dto));
    }

    @PostMapping("/{id}/voice")
    public ApiResult<SubtitleVO> generateVoice(@PathVariable Long id) {
        return ApiResult.ok(subtitleService.generateVoice(id));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        subtitleService.delete(id);
        return ApiResult.ok();
    }
}
