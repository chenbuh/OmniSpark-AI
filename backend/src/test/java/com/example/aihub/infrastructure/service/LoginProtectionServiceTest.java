package com.example.aihub.infrastructure.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginProtectionServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private LoginProtectionService service;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new LoginProtectionService(redisTemplate);
    }

    @Test
    void allowsLoginWhenThresholdNotExceeded() {
        when(valueOperations.get("auth:login:fail:username:admin")).thenReturn("7");
        when(valueOperations.get("auth:login:fail:username-ip:admin:127.0.0.1")).thenReturn("4");
        when(valueOperations.get("auth:login:fail:device-username:device-a:admin")).thenReturn("5");

        assertThatCode(() -> service.ensureAllowed("Admin", "127.0.0.1", "Device-A"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksLoginWhenUsernameAndIpThresholdExceeded() {
        when(valueOperations.get("auth:login:fail:username:admin")).thenReturn("2");
        when(valueOperations.get("auth:login:fail:username-ip:admin:127.0.0.1")).thenReturn("5");

        assertThatThrownBy(() -> service.ensureAllowed("admin", "127.0.0.1", null))
                .hasMessage("登录尝试过于频繁，请稍后再试");
    }

    @Test
    void incrementsAllRelevantFailureBuckets() {
        when(valueOperations.increment(any(String.class))).thenReturn(1L);

        service.recordFailure("Admin", "127.0.0.1", "Device-A");

        verify(valueOperations).increment("auth:login:fail:username:admin");
        verify(valueOperations).increment("auth:login:fail:username-ip:admin:127.0.0.1");
        verify(valueOperations).increment("auth:login:fail:device-username:device-a:admin");
        verify(redisTemplate).expire("auth:login:fail:username:admin", Duration.ofMinutes(15));
        verify(redisTemplate).expire("auth:login:fail:username-ip:admin:127.0.0.1", Duration.ofMinutes(10));
        verify(redisTemplate).expire("auth:login:fail:device-username:device-a:admin", Duration.ofMinutes(20));
    }

    @Test
    void skipsDeviceBucketWhenDeviceIdMissing() {
        when(valueOperations.increment(any(String.class))).thenReturn(1L);

        service.recordFailure("Admin", "127.0.0.1", "   ");

        verify(valueOperations, never()).increment(Mockito.contains("device-username"));
    }
}
