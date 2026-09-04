package com.debateai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.debateai.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/**
 * Report数据访问接口
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
