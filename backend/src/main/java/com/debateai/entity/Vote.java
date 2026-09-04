package com.debateai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投票实体
 */
@Data
@TableName("votes")
public class Vote {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long debateId;
    private Long voterId;
    private String votedFor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
