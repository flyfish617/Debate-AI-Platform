package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.Topic;
import org.apache.ibatis.annotations.Mapper;

/**
 * Topic数据访问接口
 */
@Mapper
public interface TopicMapper extends BaseMapper<Topic> {
}
