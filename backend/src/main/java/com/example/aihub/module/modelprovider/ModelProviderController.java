package com.example.aihub.module.modelprovider;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.annotation.RequireProjectPermission;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.dto.ModelProviderSaveDTO;
import com.example.aihub.infrastructure.dto.ModelProviderUpdateDTO;
import com.example.aihub.infrastructure.service.ModelProviderService;
import com.example.aihub.infrastructure.vo.ModelProviderVO;
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
@RequestMapping("/api/model-providers")
@SaCheckLogin
public class ModelProviderController {
    private final ModelProviderService providerService;

    @GetMapping("/meta")
    public ApiResult<java.util.Map<String, Object>> meta() {
        return ApiResult.ok(providerService.meta());
    }

    @GetMapping
    public ApiResult<List<ModelProviderVO>> list(@RequestParam(name = "projectId", required = false) Long projectId,
                                                 @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(providerService.list(projectId, PagingUtil.clampLimit(limit, 100, 100)));
    }

    @PostMapping
    @RequireProjectPermission("edit")
    public ApiResult<ModelProviderVO> create(@Valid @RequestBody ModelProviderSaveDTO dto) {
        return ApiResult.ok(providerService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResult<ModelProviderVO> update(@PathVariable Long id, @RequestBody ModelProviderUpdateDTO dto) {
        return ApiResult.ok(providerService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/test")
    public ApiResult<String> test(@PathVariable Long id) {
        return ApiResult.ok(providerService.testConnection(id));
    }
}
