package com.example.aihub.module.apikey;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.ApiKey;
import com.example.aihub.infrastructure.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/api-keys")
@SaCheckLogin
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    @GetMapping
    public ApiResult<List<ApiKey>> list() {
        return ApiResult.ok(apiKeyService.listByUser(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestParam String name) {
        var generated = apiKeyService.create(StpUtil.getLoginIdAsLong(), name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", generated.key());
        result.put("fullKey", generated.fullKey());
        return ApiResult.ok(result);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> revoke(@PathVariable Long id) {
        apiKeyService.revoke(id, StpUtil.getLoginIdAsLong());
        return ApiResult.ok();
    }
}
