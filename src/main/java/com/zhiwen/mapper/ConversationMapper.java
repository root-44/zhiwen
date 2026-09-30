package com.zhiwen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwen.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
