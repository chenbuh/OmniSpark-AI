package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 验证码校验请求。前端提交用户的作答结果与行为轨迹，后端按形态判定 + 行为风控。
 */
@Data
public class CaptchaVerifyDTO {
    @NotBlank
    private String captchaId;

    // ===== rotate 形态：用户最终把图转到的角度（0-359） =====
    private Integer angle;

    // ===== sequence 形态：用户依次点击的坐标，顺序即点击先后 =====
    private List<int[]> points;

    // ===== track 形态：用户拖动滑块的最终 x，以及全程轨迹 =====
    private Integer x;

    /**
     * 行为轨迹：每个元素为 [x, y, tMillis]，用于判定是否「像人」
     * （耗时下限、非匀速直线、有抖动）。三形态通用，前端尽量都采集。
     */
    private List<int[]> trail;
}
