package com.example.aihub.module.auth;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import com.example.aihub.infrastructure.dto.LoginDTO;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import com.example.aihub.infrastructure.dto.RegisterDTO;
import com.example.aihub.infrastructure.service.AuthService;
import com.example.aihub.infrastructure.vo.LoginVO;
import com.example.aihub.infrastructure.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginLogMapper loginLogMapper;

    @PostMapping("/login")
    public ApiResult<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String ua = request.getHeader("User-Agent");
        return ApiResult.ok(authService.login(dto, ip, ua));
    }

    @PostMapping("/register")
    public ApiResult<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return ApiResult.ok(authService.register(dto));
    }

    @PostMapping("/logout")
    @SaCheckLogin
    public ApiResult<Void> logout() {
        StpUtil.logout();
        return ApiResult.ok();
    }

    @GetMapping("/me")
    @SaCheckLogin
    public ApiResult<UserVO> me() {
        return ApiResult.ok(authService.me(Long.valueOf(String.valueOf(StpUtil.getLoginId()))));
    }

    // ===== 更新个人资料 =====

    @PutMapping("/profile")
    @SaCheckLogin
    public ApiResult<UserVO> updateProfile(@RequestParam(required = false) String nickname,
                                            @RequestParam(required = false) String avatar) {
        Long userId = Long.valueOf(String.valueOf(cn.dev33.satoken.stp.StpUtil.getLoginId()));
        return ApiResult.ok(authService.updateProfile(userId, nickname, avatar));
    }

    // ===== 修改密码 =====

    @PutMapping("/password")
    @SaCheckLogin
    public ApiResult<Void> changePassword(@RequestParam String oldPassword,
                                           @RequestParam String newPassword) {
        Long userId = Long.valueOf(String.valueOf(cn.dev33.satoken.stp.StpUtil.getLoginId()));
        authService.changePassword(userId, oldPassword, newPassword);
        return ApiResult.ok();
    }

    @GetMapping("/login-logs")
    @SaCheckLogin
    public ApiResult<java.util.List<LoginLog>> loginLogs(@RequestParam(defaultValue = "20") int limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .orderByDesc(LoginLog::getId)
                .last("LIMIT " + limit);
        return ApiResult.ok(loginLogMapper.selectList(wrapper));
    }
}
