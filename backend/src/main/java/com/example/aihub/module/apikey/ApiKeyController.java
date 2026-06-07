package com.example.aihub.module.apikey;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.service.ApiKeyService;
import com.example.aihub.infrastructure.vo.ApiKeyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/api-keys")
@SaCheckLogin
public class ApiKeyController {
    private final ApiKeyService apiKeyService;

    @GetMapping
    public ApiResult<List<ApiKeyVO>> list(@RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(apiKeyService.listByUser(StpUtil.getLoginIdAsLong(), PagingUtil.clampLimit(limit, 100, 100))
                .stream()
                .map(this::toApiKeyVO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestParam String name,
                                                 @RequestParam(required = false, defaultValue = "all") String scope,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expiresAt,
                                                 @RequestParam(required = false, defaultValue = "1000") Integer dailyQuota) {
        var generated = apiKeyService.create(StpUtil.getLoginIdAsLong(), name, scope, expiresAt, dailyQuota);
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

    private ApiKeyVO toApiKeyVO(com.example.aihub.infrastructure.entity.ApiKey apiKey) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(apiKey.getId());
        vo.setName(apiKey.getName());
        vo.setKeyPrefix(apiKey.getKeyPrefix());
        vo.setScope(apiKey.getScope());
        vo.setExpiresAt(apiKey.getExpiresAt());
        vo.setDailyQuota(apiKey.getDailyQuota());
        vo.setDailyUsed(apiKey.getDailyUsed());
        vo.setLastUsedIp(apiKey.getLastUsedIp());
        vo.setLastUserAgent(apiKey.getLastUserAgent());
        vo.setFrozenReason(apiKey.getFrozenReason());
        vo.setRiskScore(apiKey.getRiskScore());
        vo.setStatus(apiKey.getStatus());
        vo.setLastUsedAt(apiKey.getLastUsedAt());
        vo.setCreatedAt(apiKey.getCreatedAt());
        vo.setQuotaResetDate(apiKey.getQuotaResetDate());
        return vo;
    }
}
