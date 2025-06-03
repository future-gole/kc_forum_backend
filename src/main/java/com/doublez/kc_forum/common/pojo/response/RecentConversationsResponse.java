package com.doublez.kc_forum.common.pojo.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecentConversationsResponse {
    //消息id
    private Long id;
    //对方用户id
    private Long contactId;
    //对方用户nickName
    private String contactNickname;
    //对方用户头像
    private String contactAvatar;
    //双方的最后一条消息
    private String lastMessageContent;
    //最后一条消息时间搓
    private LocalDateTime lastMessageTimestamp;
    //未读数目
    private Integer unreadCount;
}
