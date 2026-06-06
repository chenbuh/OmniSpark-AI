package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.SystemConfig;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system")
public class PublicSystemController {
    private static final String DEFAULT_PLATFORM_NAME = "OmniSpark AI";

    private final SystemConfigMapper configMapper;

    @GetMapping("/profile")
    public ApiResult<Map<String, Object>> profile() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platformName", resolvePlatformName());
        return ApiResult.ok(result);
    }

    private String resolvePlatformName() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "platform.name")
                .last("LIMIT 1"));
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return DEFAULT_PLATFORM_NAME;
        }
        return config.getConfigValue().trim();
    }
}
