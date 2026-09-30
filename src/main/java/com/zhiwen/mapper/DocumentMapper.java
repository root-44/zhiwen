package com.zhiwen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwen.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/** 文档表 CRUD,MyBatis-Plus BaseMapper 提供通用方法 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
