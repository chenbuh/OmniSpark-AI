package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.infrastructure.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTotpServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AdminTotpService service;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AdminTotpService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "issuer", "OmniSpark AI");
    }

    @Test
    void beginSetupPersistsPendingStateAndBuildsOtpauthUrl() throws Exception {
        User admin = adminUser();

        AdminTotpService.PendingTotpSetup result = service.beginSetup(admin, "127.0.0.1", "JUnit", "device-1");

        assertThat(result.setupTicket()).isNotBlank();
        assertThat(result.secret()).matches("[A-Z2-7]+");
        assertThat(result.otpauthUrl()).contains("otpauth://totp/");
        assertThat(result.otpauthUrl()).contains("secret=");
        assertThat(result.issuer()).isEqualTo("OmniSpark AI");

        ArgumentCaptor<String> rawState = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("auth:totp:setup:" + result.setupTicket()), rawState.capture(), eq(Duration.ofMinutes(10)));
        AdminTotpService.PendingLoginState state = objectMapper.readValue(rawState.getValue(), AdminTotpService.PendingLoginState.class);
        assertThat(state.stage).isEqualTo("setup");
        assertThat(state.userId).isEqualTo(admin.getId());
        assertThat(state.pendingSecret).isEqualTo(result.secret());
    }

    @Test
    void completeSetupAcceptsCurrentTotpCode() {
        User admin = adminUser();
        AdminTotpService.PendingTotpSetup setup = service.beginSetup(admin, "127.0.0.1", "JUnit", "device-1");

        when(valueOperations.get("auth:totp:setup:" + setup.setupTicket())).thenAnswer(invocation -> {
            AdminTotpService.PendingLoginState state =
                    new AdminTotpService.PendingLoginState();
            state.userId = admin.getId();
            state.username = admin.getUsername();
            state.ip = "127.0.0.1";
            state.userAgent = "JUnit";
            state.deviceId = "device-1";
            state.stage = "setup";
            state.pendingSecret = setup.secret();
            return objectMapper.writeValueAsString(state);
        });

        String currentCode = (String) ReflectionTestUtils.invokeMethod(
                service,
                "generateTotpCode",
                setup.secret(),
                System.currentTimeMillis() / 1000 / 30
        );

        AdminTotpService.CompletedTotpChallenge result = service.completeSetup(setup.setupTicket(), currentCode);

        assertThat(result.userId()).isEqualTo(admin.getId());
        assertThat(result.secretToPersist()).isEqualTo(setup.secret());
        verify(redisTemplate).delete("auth:totp:setup:" + setup.setupTicket());
    }

    @Test
    void completeLoginRejectsInvalidCodeAndConsumesTicket() throws Exception {
        User admin = adminUser();
        AdminTotpService.PendingLoginState state = AdminTotpService.PendingLoginState.forLogin(admin, "127.0.0.1", "JUnit", "device-1");
        when(valueOperations.get("auth:totp:login:ticket-a")).thenReturn(objectMapper.writeValueAsString(state));

        assertThatThrownBy(() -> service.completeLogin("ticket-a", "000000", "JBSWY3DPEHPK3PXP"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("动态验证码无效，请重新输入");

        verify(redisTemplate).delete("auth:totp:login:ticket-a");
    }

    private User adminUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("admin");
        user.setTotpEnabled(0);
        return user;
    }
}
