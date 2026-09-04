package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * Comment数据访问接口
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
