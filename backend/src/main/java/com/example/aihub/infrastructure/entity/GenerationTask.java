package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("generation_task")
public class GenerationTask extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long providerId;
    private String taskType;
    private String prompt;
    private String negativePrompt;
    private String status;
    private Integer progress;
    private String progressText;
    private String modelName;
    @TableField("request_json")
    private String requestJson;
    @TableField("response_json")
    private String responseJson;
    private Long resultAssetId;
    private String errorMessage;
}
