package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.PasswordUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.LoginDTO;
import com.example.aihub.infrastructure.dto.LoginTotpDTO;
import com.example.aihub.infrastructure.dto.LoginTotpSetupDTO;
import com.example.aihub.infrastructure.dto.RegisterDTO;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import com.example.aihub.infrastructure.mapper.QuotaRecordMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.LoginVO;
import com.example.aihub.infrastructure.vo.PasswordChangeResultVO;
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
    private final LoginProtectionService loginProtectionService;
    private final AdminTotpService adminTotpService;

    public LoginVO login(LoginDTO dto, String ip, String userAgent) {
        String username = normalizeUsername(dto.getUsername());
        String deviceId = normalizeDeviceId(dto.getDeviceId());
        loginProtectionService.ensureAllowed(username, ip, deviceId);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            loginProtectionService.recordFailure(username, ip, deviceId);
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 登录成功后平滑升级历史 SHA-256 和低 cost 的 BCrypt 哈希
        if (PasswordUtil.needsRehash(user.getPassword())) {
            user.setPassword(PasswordUtil.encode(dto.getPassword()));
            userMapper.updateById(user);
        }

        boolean isAdmin = adminTotpService.isAdmin(user);
        boolean hasEnabledTotp = hasEnabledTotp(user);
        if (isAdmin) {
            if (!hasEnabledTotp) {
                AdminTotpService.PendingTotpSetup pendingSetup = adminTotpService.beginSetup(user, ip, userAgent, deviceId);
                LoginVO vo = new LoginVO();
                vo.setRequiresTotpSetup(true);
                vo.setSetupTicket(pendingSetup.setupTicket());
                vo.setTotpSecret(pendingSetup.secret());
                vo.setTotpOtpauthUrl(pendingSetup.otpauthUrl());
                vo.setTotpIssuer(pendingSetup.issuer());
                return vo;
            }
            String loginTicket = adminTotpService.beginLoginChallenge(user, ip, userAgent, deviceId);
            LoginVO vo = new LoginVO();
            vo.setRequiresTotp(true);
            vo.setLoginTicket(loginTicket);
            return vo;
        }

        if (hasEnabledTotp) {
            String loginTicket = adminTotpService.beginLoginChallenge(user, ip, userAgent, deviceId);
            LoginVO vo = new LoginVO();
            vo.setRequiresTotp(true);
            vo.setLoginTicket(loginTicket);
            return vo;
        }

        return finalizeLogin(user, ip, userAgent, deviceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO completeTotpSetup(LoginTotpSetupDTO dto) {
        AdminTotpService.CompletedTotpChallenge challenge = adminTotpService.completeSetup(dto.getSetupTicket(), dto.getTotpCode());
        User user = requireUser(challenge.userId());
        user.setTotpSecret(challenge.secretToPersist());
        user.setTotpEnabled(1);
        userMapper.updateById(user);
        User refreshed = requireUser(user.getId());
        return finalizeLogin(refreshed, challenge.ip(), challenge.userAgent(), challenge.deviceId());
    }

    public LoginVO completeTotpLogin(LoginTotpDTO dto) {
        AdminTotpService.PendingLoginState state = adminTotpService.peekLoginState(dto.getLoginTicket());
        User user = requireUser(state.userId);
        adminTotpService.completeLogin(dto.getLoginTicket(), dto.getTotpCode(), user.getTotpSecret());
        User refreshed = requireUser(user.getId());
        return finalizeLogin(refreshed, state.ip, state.userAgent, state.deviceId);
    }

    public LoginVO beginTotpReset(Long userId, String ip, String userAgent) {
        User user = requireUser(userId);
        AdminTotpService.PendingTotpSetup pendingSetup = adminTotpService.beginSetup(user, ip, userAgent, null);
        LoginVO vo = new LoginVO();
        vo.setRequiresTotpSetup(true);
        vo.setSetupTicket(pendingSetup.setupTicket());
        vo.setTotpSecret(pendingSetup.secret());
        vo.setTotpOtpauthUrl(pendingSetup.otpauthUrl());
        vo.setTotpIssuer(pendingSetup.issuer());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO confirmTotpReset(Long userId, LoginTotpSetupDTO dto) {
        User user = requireUser(userId);
        AdminTotpService.CompletedTotpChallenge challenge = adminTotpService.completeSetup(dto.getSetupTicket(), dto.getTotpCode());
        if (!userId.equals(challenge.userId())) {
            throw new BusinessException("验证器重置会话与当前账号不匹配，请重新操作");
        }
        user.setTotpSecret(challenge.secretToPersist());
        user.setTotpEnabled(1);
        userMapper.updateById(user);
        return toUserVO(requireUser(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("该账号已存在");
        }
        validatePassword(dto.getPassword(), dto.getUsername());

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setAvatar("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=200");
        user.setRole("user");
        user.setStatus(1);
        int affected = userMapper.insert(user);
        if (affected <= 0 || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException("注册结果待确认");
        }
        User created = userMapper.selectById(user.getId());
        if (created == null
                || !dto.getUsername().equals(created.getUsername())
                || !dto.getNickname().equals(created.getNickname())
                || created.getStatus() == null
                || created.getStatus() != 1
                || !"user".equals(created.getRole())
                || !PasswordUtil.matches(dto.getPassword(), created.getPassword())) {
            throw new BusinessException("注册结果待确认");
        }
        return toUserVO(created);
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
    public PasswordChangeResultVO changePassword(Long userId, String oldPassword, String newPassword) {
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
        validatePassword(newPassword, user.getUsername());
        String encodedPassword = PasswordUtil.encode(newPassword);
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new BusinessException("密码修改结果待确认");
        }
        if (PasswordUtil.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        user.setPassword(encodedPassword);
        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            throw new BusinessException("密码修改结果待确认");
        }
        User refreshed = userMapper.selectById(userId);
        if (refreshed == null
                || refreshed.getUpdatedAt() == null
                || !PasswordUtil.matches(newPassword, refreshed.getPassword())
                || PasswordUtil.matches(oldPassword, refreshed.getPassword())) {
            throw new BusinessException("密码修改结果待确认");
        }
        PasswordChangeResultVO result = new PasswordChangeResultVO();
        result.setUserId(refreshed.getId());
        result.setChanged(true);
        result.setUpdatedAt(refreshed.getUpdatedAt());
        return result;
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

    private void validatePassword(String rawPassword, String username) {
        String error = PasswordUtil.getPasswordValidationError(rawPassword, username);
        if (error != null) {
            throw new BusinessException(error);
        }
    }

    private LoginVO finalizeLogin(User user, String ip, String userAgent, String deviceId) {
        cn.dev33.satoken.stp.StpUtil.login(user.getId());
        loginProtectionService.clearFailures(user.getUsername(), ip, deviceId);
        try {
            LoginLog log = new LoginLog();
            log.setUserId(user.getId());
            log.setUsername(user.getUsername());
            log.setIp(ip);
            log.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
            loginLogMapper.insert(log);
        } catch (Exception ignored) {
        }

        LoginVO vo = new LoginVO();
        vo.setToken(cn.dev33.satoken.stp.StpUtil.getTokenValue());
        vo.setUserInfo(toUserVO(user));
        vo.setRequiresTotp(false);
        vo.setRequiresTotpSetup(false);
        return vo;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        return user;
    }

    private boolean hasEnabledTotp(User user) {
        return user != null
                && user.getTotpEnabled() != null
                && user.getTotpEnabled() == 1
                && user.getTotpSecret() != null
                && !user.getTotpSecret().isBlank();
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        String normalized = deviceId.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
