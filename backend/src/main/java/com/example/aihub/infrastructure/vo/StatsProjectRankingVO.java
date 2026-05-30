package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StatsProjectRankingVO {
    private Integer rank;
    private Long projectId;
    private String name;
    private String description;
    private Long taskCount;
    private Long successTaskCount;
    private Double successRate;
    private Long assetCount;
    private Integer quotaUsed;
    private Double weightPercent;
    private LocalDateTime lastActiveAt;
}
