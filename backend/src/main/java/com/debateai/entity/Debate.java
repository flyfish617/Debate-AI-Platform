package com.debateai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 辩论实体
 */
@Data
@NoArgsConstructor
@TableName("debates")
public class Debate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long topicId;
    private Long userId;
    private String userStance;
    private String aiStance;
    private String aiModel;
    private String style;
    private String visibility;
    private String status;
    private Integer currentRound;
    private Integer maxRounds;
    private String winner;
    @TableField("vote_count_user")
    private Integer userVoteCount;
    @TableField("vote_count_ai")
    private Integer aiVoteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime endedAt;
    private LocalDateTime deletedAt;

}
