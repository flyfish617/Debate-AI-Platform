package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * Notification数据访问接口
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
