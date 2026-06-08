package com.example.aihub.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(
                resolver,
                "trustedProxiesConfig",
                "127.0.0.1,::1,0:0:0:0:0:0:0:1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,100.64.0.0/10"
        );
        resolver.init();
    }

    @Test
    void resolvesFirstForwardedForIpWhenRemoteAddrIsTrustedPrivateProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.3");
        request.addHeader("X-Forwarded-For", "203.0.113.8, 172.18.0.3");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.8");
    }

    @Test
    void resolvesStandardForwardedHeaderAndStripsIpv6BracketsAndPort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.2.15");
        request.addHeader("Forwarded", "for=\"[2001:db8::10]:443\";proto=https;by=10.0.2.15");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::10");
    }

    @Test
    void keepsRemoteAddrWhenSourceIsNotTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }
}
