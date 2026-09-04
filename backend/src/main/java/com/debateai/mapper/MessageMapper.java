package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * Message数据访问接口
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
