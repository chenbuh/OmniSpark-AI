package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("public_content_like")
public class PublicContentLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String resourceType;
    private Long resourceId;
    private Long userId;
    private LocalDateTime createdAt;
}
