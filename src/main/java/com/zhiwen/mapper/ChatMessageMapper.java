package com.zhiwen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwen.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
