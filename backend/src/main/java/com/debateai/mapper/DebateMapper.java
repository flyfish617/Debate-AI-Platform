package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.Debate;
import org.apache.ibatis.annotations.Mapper;

/**
 * Debate数据访问接口
 */
@Mapper
public interface DebateMapper extends BaseMapper<Debate> {
}
