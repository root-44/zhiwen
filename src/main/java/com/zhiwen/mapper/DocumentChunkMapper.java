package com.zhiwen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwen.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/** 切片表 CRUD */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
