package com.debateai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 话题实体
 */
@Data
@TableName("topics")
public class Topic {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String category;
    private Long creatorId;
    private String status;
    private Integer debateCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

}
