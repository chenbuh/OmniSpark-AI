package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.SystemConfig;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/maintenance")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminMaintenanceController {
    private final SystemConfigMapper configMapper;

    @GetMapping
    public ApiResult<Map<String, Object>> status() {
        var wrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "maintenance_mode");
        SystemConfig config = configMapper.selectOne(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", config != null && "true".equals(config.getConfigValue()));

        var msgWrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "maintenance_message");
        SystemConfig msgConfig = configMapper.selectOne(msgWrapper);
        result.put("message", msgConfig != null ? msgConfig.getConfigValue() : "系统维护中，请稍后再试");
        return ApiResult.ok(result);
    }

    @PostMapping
    public ApiResult<Void> toggle(@RequestParam boolean enabled, @RequestParam(defaultValue = "系统维护中，请稍后再试") String message) {
        upsertConfig("maintenance_mode", enabled ? "true" : "false", "maintenance", "维护模式开关");
        upsertConfig("maintenance_message", message, "maintenance", "维护提示消息");
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
}
