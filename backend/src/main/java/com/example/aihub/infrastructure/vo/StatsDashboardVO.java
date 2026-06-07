package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.util.List;

@Data
public class StatsDashboardVO {
    private String scope;
    private String message;
    private StatsOverviewVO overview;
    private StatsDistributionVO distribution;
    private List<StatsTrendPointVO> trends;
    private List<StatsProjectRankingVO> projectRankings;
    private List<StatsActivityVO> recentActivities;
}
