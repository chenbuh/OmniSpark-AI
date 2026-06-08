package com.example.aihub.infrastructure.service;

import com.example.aihub.infrastructure.vo.IpGeoInfoVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class IpGeoLookupServiceTest {

    @Test
    void resolveMarksLoopbackAsLocalNetwork() {
        IpGeoLookupService service = new IpGeoLookupService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "cacheMinutes", 1L);

        IpGeoInfoVO result = service.resolve("127.0.0.1");

        assertThat(result.getSource()).isEqualTo("local");
        assertThat(result.getPrivateNetwork()).isTrue();
        assertThat(result.getLocationSummary()).contains("回环");
        assertThat(result.getCountry()).isEqualTo("本地网络");
    }
}
