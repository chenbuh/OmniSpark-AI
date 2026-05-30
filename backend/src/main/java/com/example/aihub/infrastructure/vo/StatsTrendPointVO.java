package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class StatsTrendPointVO {
    private String date;
    private Long taskCount;
    private Integer quotaUsed;
}
