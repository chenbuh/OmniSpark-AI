package com.example.aihub.module.generation;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.ImageGenerateDTO;
import com.example.aihub.infrastructure.dto.VideoGenerateDTO;
import com.example.aihub.infrastructure.service.GenerationService;
import com.example.aihub.infrastructure.vo.GenerationTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/generation")
@SaCheckLogin
public class GenerationController {
    private final GenerationService generationService;

    @GetMapping("/meta")
    public ApiResult<java.util.Map<String, Object>> meta() {
        return ApiResult.ok(generationService.meta());
    }

    @PostMapping("/image")
    @RateLimit(count = 10, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "生成请求过于频繁，请稍后再试")
    @RateLimit(count = 200, seconds = 86400, dimension = RateLimit.Dimension.USER_API, message = "今日生成次数已达上限，请明日再试")
    public ApiResult<GenerationTaskVO> image(@Valid @RequestBody ImageGenerateDTO dto) {
        return ApiResult.ok(generationService.generateImage(dto));
    }

    @PostMapping("/video")
    @RateLimit(count = 10, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "生成请求过于频繁，请稍后再试")
    @RateLimit(count = 200, seconds = 86400, dimension = RateLimit.Dimension.USER_API, message = "今日生成次数已达上限，请明日再试")
    public ApiResult<GenerationTaskVO> video(@Valid @RequestBody VideoGenerateDTO dto) {
        return ApiResult.ok(generationService.generateVideo(dto));
    }

    @PostMapping("/image/inpaint")
    @RateLimit(count = 10, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "生成请求过于频繁，请稍后再试")
    @RateLimit(count = 200, seconds = 86400, dimension = RateLimit.Dimension.USER_API, message = "今日生成次数已达上限，请明日再试")
    public ApiResult<GenerationTaskVO> inpaint(@Valid @RequestBody ImageGenerateDTO dto) {
        return ApiResult.ok(generationService.generateInpaint(dto));
    }
}
