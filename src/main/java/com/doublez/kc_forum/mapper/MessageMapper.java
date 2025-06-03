package com.doublez.kc_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doublez.kc_forum.common.pojo.response.RecentConversationsResponse;
import com.doublez.kc_forum.model.Message;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    /**
     * 获取指定用户的最近会话列表
     * @param currentUserId 当前用户ID
     * @return 最近会话列表
     */
    List<RecentConversationsResponse> getRecentConversations(Long currentUserId);
}
