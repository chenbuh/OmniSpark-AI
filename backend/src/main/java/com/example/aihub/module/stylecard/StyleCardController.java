package com.example.aihub.module.stylecard;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.StyleCardSaveDTO;
import com.example.aihub.infrastructure.service.StyleCardService;
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

    @GetMapping
    public ApiResult<List<StyleCardVO>> list(@RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) String type) {
        return ApiResult.ok(styleCardService.list(projectId, type));
    }

    @GetMapping("/{id}")
    public ApiResult<StyleCardVO> get(@PathVariable Long id) {
        return ApiResult.ok(styleCardService.get(id));
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
}
