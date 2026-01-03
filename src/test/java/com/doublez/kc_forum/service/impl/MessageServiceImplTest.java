package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.response.RecentConversationsResponse;
import com.doublez.kc_forum.mapper.MessageMapper;
import com.doublez.kc_forum.model.Message;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IUserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private IUserService userService;

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Message.class);
    }

    @Test
    void create_Success() {
        Message message = new Message();
        message.setReceiveUserId(1L);
        message.setPostUserId(2L);
        message.setContent("Hello");

        when(userService.selectUserInfoById(1L)).thenReturn(new User());
        when(userService.selectUserInfoById(2L)).thenReturn(new User());
        when(messageMapper.insert(message)).thenReturn(1);

        assertDoesNotThrow(() -> messageService.create(message));
    }

    @Test
    void create_InvalidMessage_ThrowsException() {
        Message message = new Message(); // Missing fields
        assertThrows(BusinessException.class, () -> messageService.create(message));
    }

    @Test
    void create_UserNotFound_ThrowsException() {
        Message message = new Message();
        message.setReceiveUserId(1L);
        message.setPostUserId(2L);
        message.setContent("Hello");

        when(userService.selectUserInfoById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> messageService.create(message));
    }

    @Test
    void create_InsertFailed_ThrowsException() {
        Message message = new Message();
        message.setReceiveUserId(1L);
        message.setPostUserId(2L);
        message.setContent("Hello");

        when(userService.selectUserInfoById(1L)).thenReturn(new User());
        when(userService.selectUserInfoById(2L)).thenReturn(new User());
        when(messageMapper.insert(message)).thenReturn(0);

        assertThrows(SystemException.class, () -> messageService.create(message));
    }

    @Test
    void selectUnreadCount_Success() {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        long count = messageService.selectUnreadCount(1L);
        assertEquals(5L, count);
    }

    @Test
    void selectAllMessageByPostUserID_Success() {
        Long receiveUserId = 1L;
        Long postUserId = 2L;
        List<Message> messages = new ArrayList<>();
        Message msg = new Message();
        msg.setId(100L);
        msg.setState((byte)0);
        messages.add(msg);

        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(messages);
        when(messageMapper.update(any(Message.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        List<Message> result = messageService.selectAllMessageByPostUserID(receiveUserId, postUserId);

        assertEquals(1, result.size());
        // Verify update was called to mark as read
        verify(messageMapper).update(any(Message.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void getRecentConversations_Success() {
        List<RecentConversationsResponse> responses = new ArrayList<>();
        responses.add(new RecentConversationsResponse());
        when(messageMapper.getRecentConversations(1L)).thenReturn(responses);

        List<RecentConversationsResponse> result = messageService.getRecentConversations(1L);
        assertEquals(1, result.size());
    }
}
