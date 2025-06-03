package com.doublez.kc_forum.service;

import com.doublez.kc_forum.model.Message;

import java.util.List;

public interface IMessageService {
    /**
     * 发送消息
     * @param message
     */
    void create(Message message);

    /**
     * 查询未读信件
     * @param userId
     * @return
     */
    long selectUnreadCount(Long userId);

    List<Message> selectAllMessageByPostUserID(Long receiveUserId, Long postUserId);
}
