package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpInterface;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig {
    private final UserMapper userMapper;

    @Bean
    public StpInterface stpInterface() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return Collections.emptyList();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                User user = userMapper.selectById(Long.valueOf(String.valueOf(loginId)));
                if (user == null) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(user.getRole());
            }
        };
    }
}
