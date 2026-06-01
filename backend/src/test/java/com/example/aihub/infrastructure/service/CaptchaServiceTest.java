package com.example.aihub.infrastructure.service;

import com.example.aihub.infrastructure.dto.CaptchaVerifyDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaptchaServiceTest {
    private final CaptchaService service = new CaptchaService(null, new ObjectMapper());

    @Test
    void rotateAllowsSingleDelayedTrailPoint() {
        CaptchaService.Answer answer = CaptchaService.Answer.rotate(90);

        assertThat(looksHuman(answer, List.of(new int[]{90, 0, 450}))).isTrue();
    }

    @Test
    void sequenceAllowsOneTrailPointPerClick() {
        CaptchaService.Answer answer = CaptchaService.Answer.sequence(List.of(
                new int[]{40, 40},
                new int[]{80, 80},
                new int[]{120, 120}
        ));

        assertThat(looksHuman(answer, List.of(
                new int[]{40, 40, 420},
                new int[]{80, 80, 560},
                new int[]{120, 120, 690}
        ))).isTrue();
    }

    @Test
    void sequenceAllowsClicksOnVisibleShapeEdges() {
        CaptchaService.Answer answer = CaptchaService.Answer.sequence(List.of(
                new int[]{100, 100}
        ));
        CaptchaVerifyDTO dto = new CaptchaVerifyDTO();
        dto.setPoints(List.of(new int[]{126, 126}));

        Boolean result = ReflectionTestUtils.invokeMethod(service, "verifySequence", answer, dto);

        assertThat(result).isTrue();
    }

    @Test
    void trackRejectsTooFastTrail() {
        CaptchaService.Answer answer = CaptchaService.Answer.track(230);

        assertThat(looksHuman(answer, List.of(
                new int[]{40, 60, 0},
                new int[]{90, 70, 90},
                new int[]{150, 80, 180},
                new int[]{230, 90, 250}
        ))).isFalse();
    }

    @Test
    void trackRejectsUniformScriptedTrail() {
        CaptchaService.Answer answer = CaptchaService.Answer.track(220);

        assertThat(looksHuman(answer, List.of(
                new int[]{40, 60, 0},
                new int[]{100, 60, 100},
                new int[]{160, 60, 200},
                new int[]{220, 60, 300},
                new int[]{220, 60, 330}
        ))).isFalse();
    }

    @Test
    void trackAllowsHumanLikeTrail() {
        CaptchaService.Answer answer = CaptchaService.Answer.track(225);

        assertThat(looksHuman(answer, List.of(
                new int[]{40, 62, 0},
                new int[]{74, 66, 72},
                new int[]{129, 83, 151},
                new int[]{177, 78, 263},
                new int[]{225, 91, 430}
        ))).isTrue();
    }

    private boolean looksHuman(CaptchaService.Answer answer, List<int[]> trail) {
        Boolean result = ReflectionTestUtils.invokeMethod(service, "looksHuman", answer, trail);
        return Boolean.TRUE.equals(result);
    }
}
