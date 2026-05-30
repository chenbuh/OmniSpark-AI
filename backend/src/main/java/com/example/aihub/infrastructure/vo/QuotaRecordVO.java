package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class QuotaRecordVO {
    private Long id;
    private Long userId;
    private Long projectId;
    private Long taskId;
    private String quotaType;
    private Integer amount;
    private String remark;
    private java.time.LocalDateTime createdAt;
}
