package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.SystemConfig;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/maintenance")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminMaintenanceController {
    private static final String DEFAULT_MESSAGE = "系统维护中，请稍后再试";
    private static final String MAINTENANCE_MODE_KEY = "maintenance_mode";
    private static final String MAINTENANCE_MESSAGE_KEY = "maintenance_message";

    private final SystemConfigMapper configMapper;

    @GetMapping
    public ApiResult<Map<String, Object>> status() {
        var wrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, MAINTENANCE_MODE_KEY);
        SystemConfig config = configMapper.selectOne(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", config != null && "true".equals(config.getConfigValue()));

        var msgWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, MAINTENANCE_MESSAGE_KEY);
        SystemConfig msgConfig = configMapper.selectOne(msgWrapper);
        result.put("message", normalizeMessage(msgConfig != null ? msgConfig.getConfigValue() : null));
        return ApiResult.ok(result);
    }

    @PostMapping
    @RateLimit(count = 10, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "操作过于频繁")
    public ApiResult<Void> toggle(@RequestParam boolean enabled, @RequestParam(defaultValue = DEFAULT_MESSAGE) String message) {
        upsertConfig(MAINTENANCE_MODE_KEY, enabled ? "true" : "false", "maintenance", "维护模式开关");
        upsertConfig(MAINTENANCE_MESSAGE_KEY, normalizeMessage(message), "maintenance", "维护提示消息");
        return ApiResult.ok();
    }

    private void upsertConfig(String key, String value, String group, String remark) {
        var wrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key);
        SystemConfig config = configMapper.selectOne(wrapper);
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigGroup(group);
            config.setRemark(remark);
            configMapper.insert(config);
        } else {
            config.setConfigValue(value);
            configMapper.updateById(config);
        }
    }

    private String normalizeMessage(String message) {
        return message == null || message.isBlank() ? DEFAULT_MESSAGE : message.trim();
    }
}
