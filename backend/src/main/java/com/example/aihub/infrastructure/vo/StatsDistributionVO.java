package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class StatsDistributionVO {
    private Long imageTaskCount;
    private Long videoTaskCount;
    private Long successTaskCount;
    private Long runningTaskCount;
    private Long failedTaskCount;
}
