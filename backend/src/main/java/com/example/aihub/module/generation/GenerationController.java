package com.example.aihub.module.generation;

import cn.dev33.satoken.annotation.SaCheckLogin;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/generation")
@SaCheckLogin
public class GenerationController {
    private final GenerationService generationService;

    @PostMapping("/image")
    public ApiResult<GenerationTaskVO> image(@Valid @RequestBody ImageGenerateDTO dto) {
        return ApiResult.ok(generationService.generateImage(dto));
    }

    @PostMapping("/video")
    public ApiResult<GenerationTaskVO> video(@Valid @RequestBody VideoGenerateDTO dto) {
        return ApiResult.ok(generationService.generateVideo(dto));
    }

    @PostMapping("/image/inpaint")
    public ApiResult<GenerationTaskVO> inpaint(@Valid @RequestBody ImageGenerateDTO dto) {
        return ApiResult.ok(generationService.generateInpaint(dto));
    }
}
