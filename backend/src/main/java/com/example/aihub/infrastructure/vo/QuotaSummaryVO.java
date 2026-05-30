package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class QuotaSummaryVO {
    private Integer quotaLimit;
    private Integer quotaUsed;
    private Integer remaining;
}
