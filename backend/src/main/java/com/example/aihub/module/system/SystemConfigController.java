package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.security.SensitiveConfigPolicy;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.SystemConfig;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import com.example.aihub.infrastructure.vo.SystemConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/config")
@SaCheckLogin
@SaCheckRole("admin")
public class SystemConfigController {
    private final SystemConfigMapper configMapper;

    @GetMapping
    public ApiResult<List<SystemConfigVO>> list(@RequestParam(required = false) String group,
                                                @RequestParam(defaultValue = "100") int limit) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            wrapper.eq(SystemConfig::getConfigGroup, group);
        }
        wrapper.orderByDesc(SystemConfig::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100));
        return ApiResult.ok(configMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList()));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<SystemConfigVO>> page(@RequestParam(required = false) String group,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "100") long pageSize) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            wrapper.eq(SystemConfig::getConfigGroup, group);
        }
        wrapper.orderByDesc(SystemConfig::getId);
        Page<SystemConfig> result = configMapper.selectPage(
                new Page<>(PagingUtil.normalizePage(page), PagingUtil.clampPageSize(pageSize, 100)),
                wrapper
        );
        return ApiResult.ok(new PageResult<>(result.getTotal(), result.getPages(),
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList())));
    }

    @PutMapping("/{id}")
    @RateLimit(count = 30, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "修改配置过于频繁")
    public ApiResult<Void> update(@PathVariable Long id, @RequestParam String value) {
        SystemConfig config = configMapper.selectById(id);
        if (config == null) return ApiResult.fail("配置项不存在");
        config.setConfigValue(value);
        configMapper.updateById(config);
        return ApiResult.ok();
    }

    private SystemConfigVO toVO(SystemConfig config) {
        boolean sensitive = SensitiveConfigPolicy.isSensitiveConfigKey(config.getConfigKey());
        SystemConfigVO vo = new SystemConfigVO();
        vo.setId(config.getId());
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigValue(sensitive ? SensitiveConfigPolicy.maskSensitiveValue(config.getConfigValue()) : config.getConfigValue());
        vo.setConfigGroup(config.getConfigGroup());
        vo.setRemark(config.getRemark());
        vo.setSensitive(sensitive);
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }
}
