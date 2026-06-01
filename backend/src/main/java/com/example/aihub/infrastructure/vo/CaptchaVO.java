package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.util.List;

/**
 * 验证码下发数据。答案（旋转角度 / 点击顺序坐标 / 轨道终点）一律不在此对象中，
 * 只存在于后端 Redis，前端拿不到。
 */
@Data
public class CaptchaVO {
    /** 本次验证码的唯一标识，校验时回传。 */
    private String captchaId;

    /** 形态：rotate / sequence / track。 */
    private String type;

    /** 画布宽度（px）。 */
    private int width;

    /** 画布高度（px）。 */
    private int height;

    /** 主图（base64 PNG，带 data uri 前缀）。三形态通用：旋转图 / 点击底图 / 轨迹底图。 */
    private String image;

    /** 提示文案，例如「请将图片转正」「依次点击：方块→三角→圆」。 */
    private String prompt;

    // ===== rotate 形态专用 =====
    /** 旋转盘的小图（base64）。为 null 表示该形态不使用。 */
    private String thumb;

    // ===== sequence 形态专用 =====
    /** 需要点击的目标在图上的可见标号/图标顺序提示（仅展示用，不含坐标答案）。 */
    private List<String> sequenceLabels;

    // ===== track 形态专用 =====
    /** 轨道的可见路径点（用于前端画出轨道，终点判定仍在后端）。 */
    private List<int[]> trackPath;
    /** 滑块在 y 轴的固定位置。 */
    private Integer trackY;
}
