package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import com.example.aihub.infrastructure.vo.IpGeoInfoVO;
import com.example.aihub.infrastructure.vo.LoginLogAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminLoginLogService {
    private final LoginLogMapper loginLogMapper;
    private final IpGeoLookupService ipGeoLookupService;

    public PageResult<LoginLogAdminVO> page(Long userId, String username, String ip, long page, long pageSize) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(LoginLog::getUserId, userId);
        }
        if (username != null && !username.isBlank()) {
            wrapper.like(LoginLog::getUsername, username.trim());
        }
        if (ip != null && !ip.isBlank()) {
            wrapper.like(LoginLog::getIp, ip.trim());
        }
        wrapper.orderByDesc(LoginLog::getId);
        Page<LoginLog> result = loginLogMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        Map<String, IpGeoInfoVO> ipGeoMap = ipGeoLookupService.resolveBatch(result.getRecords().stream()
                .map(LoginLog::getIp)
                .filter(Objects::nonNull)
                .toList());
        List<LoginLogAdminVO> records = result.getRecords().stream().map(item -> {
            LoginLogAdminVO vo = VoMapper.copy(item, LoginLogAdminVO.class);
            vo.setIpGeo(ipGeoMap.get(item.getIp()));
            return vo;
        }).toList();
        return new PageResult<>(result.getTotal(), result.getPages(), records);
    }
}
