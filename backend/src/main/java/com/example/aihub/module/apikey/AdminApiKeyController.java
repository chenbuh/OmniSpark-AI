package com.example.aihub.module.apikey;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.ApiKey;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.service.ApiKeyService;
import com.example.aihub.infrastructure.vo.AdminApiKeyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/api-keys")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminApiKeyController {
    private final ApiKeyService apiKeyService;
    private final UserMapper userMapper;

    @GetMapping
    public ApiResult<List<AdminApiKeyVO>> list(@RequestParam(defaultValue = "100") int limit) {
        List<ApiKey> keys = apiKeyService.listAll(PagingUtil.clampLimit(limit, 100, 100));
        if (keys.isEmpty()) return ApiResult.ok(List.of());

        // 收集所有 user_id 一次查完
        List<Long> userIds = keys.stream().map(ApiKey::getUserId).distinct().collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> usernameMap = users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        List<AdminApiKeyVO> result = new ArrayList<>(keys.size());
        for (ApiKey k : keys) {
            AdminApiKeyVO vo = new AdminApiKeyVO();
            vo.setId(k.getId());
            vo.setUserId(k.getUserId());
            vo.setUsername(usernameMap.getOrDefault(k.getUserId(), "未知"));
            vo.setName(k.getName());
            vo.setKeyPrefix(k.getKeyPrefix());
            vo.setScope(k.getScope());
            vo.setExpiresAt(k.getExpiresAt());
            vo.setDailyQuota(k.getDailyQuota());
            vo.setDailyUsed(k.getDailyUsed());
            vo.setLastUsedIp(k.getLastUsedIp());
            vo.setLastUserAgent(k.getLastUserAgent());
            vo.setFrozenReason(k.getFrozenReason());
            vo.setRiskScore(k.getRiskScore());
            vo.setStatus(k.getStatus());
            vo.setLastUsedAt(k.getLastUsedAt());
            vo.setCreatedAt(k.getCreatedAt());
            result.add(vo);
        }
        return ApiResult.ok(result);
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestParam String name,
                                                  @RequestParam(name = "userId", required = false, defaultValue = "0") Long userId,
                                                  @RequestParam(required = false, defaultValue = "all") String scope,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expiresAt,
                                                  @RequestParam(required = false, defaultValue = "1000") Integer dailyQuota) {
        Map<String, Object> result = apiKeyService.generate(name, userId, scope, expiresAt, dailyQuota);
        return ApiResult.ok(result);
    }

    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestParam String name) {
        apiKeyService.updateName(id, name);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> revoke(@PathVariable Long id,
                                   @RequestParam(required = false, defaultValue = "false") boolean force) {
        if (force) {
            apiKeyService.deleteByAdmin(id);
        } else {
            apiKeyService.revokeByAdmin(id);
        }
        return ApiResult.ok();
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResult<Void> unfreeze(@PathVariable Long id) {
        apiKeyService.unfreezeByAdmin(id);
        return ApiResult.ok();
    }
}
