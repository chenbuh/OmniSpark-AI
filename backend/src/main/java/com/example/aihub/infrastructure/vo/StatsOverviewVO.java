package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class StatsOverviewVO {
    private Long projectCount;
    private Long taskCount;
    private Long successTaskCount;
    private Long assetCount;
    private Long favoriteAssetCount;
    private Integer quotaUsed;
    private Integer quotaLimit;
}
