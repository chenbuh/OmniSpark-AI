package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("webhook")
public class Webhook {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String url;
    private String events;
    private String secret;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
