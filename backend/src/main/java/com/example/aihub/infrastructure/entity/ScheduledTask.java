package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scheduled_task")
public class ScheduledTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String cron;
    private Integer enabled;
    private String taskType;
    @TableField("config_json")
    private String configJson;
    private LocalDateTime lastRunAt;
    private String lastStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
