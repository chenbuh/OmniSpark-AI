package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.SystemConfig;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/config")
@SaCheckLogin
@SaCheckRole("admin")
public class SystemConfigController {
    private final SystemConfigMapper configMapper;

    @GetMapping
    public ApiResult<List<SystemConfig>> list(@RequestParam(required = false) String group,
                                              @RequestParam(defaultValue = "100") int limit) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            wrapper.eq(SystemConfig::getConfigGroup, group);
        }
        wrapper.orderByDesc(SystemConfig::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100));
        return ApiResult.ok(configMapper.selectList(wrapper));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<SystemConfig>> page(@RequestParam(required = false) String group,
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
        return ApiResult.ok(new PageResult<>(result.getTotal(), result.getPages(), result.getRecords()));
    }

    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestParam String value) {
        SystemConfig config = configMapper.selectById(id);
        if (config == null) return ApiResult.fail("配置项不存在");
        config.setConfigValue(value);
        configMapper.updateById(config);
        return ApiResult.ok();
    }
}
