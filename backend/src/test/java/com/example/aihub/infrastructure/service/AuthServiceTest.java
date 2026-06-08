package com.example.aihub.infrastructure.service;

import com.example.aihub.common.util.PasswordUtil;
import com.example.aihub.infrastructure.dto.LoginDTO;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import com.example.aihub.infrastructure.mapper.QuotaRecordMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserMapper userMapper;
    private QuotaRecordMapper quotaRecordMapper;
    private LoginLogMapper loginLogMapper;
    private LoginProtectionService loginProtectionService;
    private AdminTotpService adminTotpService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userMapper = Mockito.mock(UserMapper.class);
        quotaRecordMapper = Mockito.mock(QuotaRecordMapper.class);
        loginLogMapper = Mockito.mock(LoginLogMapper.class);
        loginProtectionService = Mockito.mock(LoginProtectionService.class);
        adminTotpService = Mockito.mock(AdminTotpService.class);
        service = new AuthService(userMapper, quotaRecordMapper, loginLogMapper, loginProtectionService, adminTotpService);
    }

    @Test
    void loginRequiresTotpForRegularUserWhoEnabledVerifier() {
        User user = regularUser();
        user.setPassword(PasswordUtil.encode("Passw0rd!"));
        user.setTotpEnabled(1);
        user.setTotpSecret("JBSWY3DPEHPK3PXP");

        LoginDTO dto = new LoginDTO();
        dto.setUsername(user.getUsername());
        dto.setPassword("Passw0rd!");
        dto.setDeviceId("device-1");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(adminTotpService.isAdmin(any(User.class))).thenReturn(false);
        when(adminTotpService.beginLoginChallenge(user, "127.0.0.1", "JUnit", "device-1")).thenReturn("ticket-1");

        LoginVO result = service.login(dto, "127.0.0.1", "JUnit");

        assertThat(result.getRequiresTotp()).isTrue();
        assertThat(result.getLoginTicket()).isEqualTo("ticket-1");
        assertThat(result.getRequiresTotpSetup()).isNull();
        assertThat(result.getToken()).isNull();
    }

    @Test
    void beginTotpResetAllowsRegularUserToStartBinding() {
        User user = regularUser();
        when(userMapper.selectById(2L)).thenReturn(user);
        when(adminTotpService.beginSetup(user, "127.0.0.1", "JUnit", null))
                .thenReturn(new AdminTotpService.PendingTotpSetup("setup-1", "SECRET123", "otpauth://totp/demo", "OmniSpark AI"));

        LoginVO result = service.beginTotpReset(2L, "127.0.0.1", "JUnit");

        assertThat(result.getRequiresTotpSetup()).isTrue();
        assertThat(result.getSetupTicket()).isEqualTo("setup-1");
        assertThat(result.getTotpSecret()).isEqualTo("SECRET123");
        assertThat(result.getTotpOtpauthUrl()).isEqualTo("otpauth://totp/demo");
        assertThat(result.getTotpIssuer()).isEqualTo("OmniSpark AI");
    }

    private User regularUser() {
        User user = new User();
        user.setId(2L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setRole("user");
        user.setStatus(1);
        return user;
    }
}
