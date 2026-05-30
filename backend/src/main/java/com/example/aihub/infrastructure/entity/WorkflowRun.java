package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workflow_run")
public class WorkflowRun extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private Long projectId;
    private String status;
    private Integer currentStep;
    @TableField("steps_result_json")
    private String stepsResultJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
