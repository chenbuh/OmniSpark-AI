package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.PasswordUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.LoginDTO;
import com.example.aihub.infrastructure.dto.RegisterDTO;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import com.example.aihub.infrastructure.mapper.QuotaRecordMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.LoginVO;
import com.example.aihub.infrastructure.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int DEFAULT_QUOTA_LIMIT = 100;

    private final UserMapper userMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final LoginLogMapper loginLogMapper;

    public LoginVO login(LoginDTO dto, String ip, String userAgent) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 历史无盐 SHA-256 哈希在校验通过后平滑升级为 BCrypt
        if (PasswordUtil.isLegacyHash(user.getPassword())) {
            user.setPassword(PasswordUtil.encode(dto.getPassword()));
            userMapper.updateById(user);
        }

        cn.dev33.satoken.stp.StpUtil.login(user.getId());
        // 记录登录日志
        try {
            LoginLog log = new LoginLog();
            log.setUserId(user.getId());
            log.setUsername(user.getUsername());
            log.setIp(ip);
            log.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
            loginLogMapper.insert(log);
        } catch (Exception ignored) {}

        UserVO userVO = toUserVO(user);
        LoginVO vo = new LoginVO();
        vo.setToken(cn.dev33.satoken.stp.StpUtil.getTokenValue());
        vo.setUserInfo(userVO);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("该账号已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encode(dto.getPasswordHash()));
        user.setNickname(dto.getNickname());
        user.setAvatar("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=200");
        user.setRole("user");
        user.setStatus(1);
        userMapper.insert(user);
        return toUserVO(user);
    }

    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toUserVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, String nickname, String avatar) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (nickname != null && !nickname.isBlank()) {
            user.setNickname(nickname);
        }
        if (avatar != null && !avatar.isBlank()) {
            user.setAvatar(avatar);
        }
        userMapper.updateById(user);
        return toUserVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException("请输入当前密码");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("请输入新密码");
        }
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        user.setPassword(PasswordUtil.encode(newPassword));
        userMapper.updateById(user);
    }

    public User findById(Long userId) {
        return userMapper.selectById(userId);
    }

    private UserVO toUserVO(User user) {
        UserVO vo = VoMapper.copy(user, UserVO.class);
        vo.setQuotaLimit(DEFAULT_QUOTA_LIMIT);
        vo.setQuotaUsed(getQuotaUsed(user.getId()));
        return vo;
    }

    private int getQuotaUsed(Long userId) {
        List<Integer> values = quotaRecordMapper.selectList(new LambdaQueryWrapper<com.example.aihub.infrastructure.entity.QuotaRecord>()
                        .eq(com.example.aihub.infrastructure.entity.QuotaRecord::getUserId, userId))
                .stream()
                .map(item -> item.getAmount() == null ? 0 : item.getAmount())
                .toList();
        return values.stream().mapToInt(Integer::intValue).sum();
    }
}
