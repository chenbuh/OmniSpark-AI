package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("style_card")
public class StyleCard extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String name;
    private String type;
    private String content;
    private String negativePrompt;
    private String modelName;
    private Long providerId;
    private Long refAssetId;
    private Double cfg;
    private Integer steps;
    private String size;
    @TableField("params_json")
    private String paramsJson;
    private String previewUrl;
    private String tag;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer status;
}
