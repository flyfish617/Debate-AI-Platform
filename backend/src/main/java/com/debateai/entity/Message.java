package com.debateai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 辩论消息实体
 */
@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long debateId;
    private Long userId;
    private String role;
    private String content;
    private Integer round;
    private String aiProvider;
    private String aiModel;
    private Integer latencyMs;
    private String status;
    private LocalDateTime createdAt;

}
