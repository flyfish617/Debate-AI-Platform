package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.Vote;
import org.apache.ibatis.annotations.Mapper;

/**
 * Vote数据访问接口
 */
@Mapper
public interface VoteMapper extends BaseMapper<Vote> {
}
