package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * User数据访问接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
