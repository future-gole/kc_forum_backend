package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.pojo.request.MessageRequest;
import com.doublez.kc_forum.common.pojo.response.MessageResponse;
import com.doublez.kc_forum.common.pojo.response.RecentConversationsResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.Message;
import com.doublez.kc_forum.service.impl.MessageServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Tag(name = "站内信")
@Slf4j
@RequestMapping("/message")
@RestController
public class MessageController {
    @Autowired
    private MessageServiceImpl messageService;

    @PostMapping("/send")
    @Operation(summary = "发送站内信")
    public Result<?> sendMessage(@RequestBody @Valid MessageRequest messageRequest,HttpServletRequest request) {
        Long userId = JwtUtil.getUserId(request);
        if(Objects.equals(userId, messageRequest.getReceiveUserId())){
            log.warn("不能给自己发送站内信, userId = {}",userId);
            throw new BusinessException(ResultCode.ERROR_MESSAGE_NOT_VALID);
        }
        log.info("用户id:{}发送消息给 用户id：{}", userId,messageRequest.getReceiveUserId());
        Message message = new Message();
        BeanUtils.copyProperties(messageRequest,message);
        //todo异常捕获
        message.setPostUserId(userId);
        messageService.create(message);
        MessageResponse messageResponse = new MessageResponse();
        BeanUtils.copyProperties(message,messageResponse);
        return Result.success(messageResponse);
    }

    @GetMapping("/getUnreadCount")
    @Operation(summary = "当前用户未读消息数")
    public Result<?> getUnreadCount(HttpServletRequest request) {
        Long userId = JwtUtil.getUserId(request);

        long count = messageService.selectUnreadCount(userId);
        log.info("用户id:{} 有{}个未读消息",userId,count);
        return Result.success(count);
    }

    @GetMapping("/getAllMessagesByUserId")
    @Operation(summary = "获取当前两个用户的会话消息",description = "会获取双方的消息，并且postUserId为发送者")
    public List<MessageResponse> getAllMessagesByUserId(@NonNull @Parameter(description = "发送者的id") Long postUserId,
                                                            HttpServletRequest request) {
        if(postUserId < 0){
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        Long receiveUserId = JwtUtil.getUserId(request);
        List<Message> messages = messageService.selectAllMessageByPostUserID(receiveUserId, postUserId);
        List<MessageResponse> messageResponses = new ArrayList<>();
        for(Message message : messages){
            MessageResponse messageResponse = new MessageResponse();
            BeanUtils.copyProperties(message,messageResponse);
            messageResponses.add(messageResponse);
        }
        log.info("用户id:{} 读取 用户id为：{}的所有消息成功！",receiveUserId,postUserId);
        return messageResponses;
    }

    @GetMapping("/getRecentConversations")
    public List<RecentConversationsResponse> getRecentConversations(HttpServletRequest request) {
        Long userId = JwtUtil.getUserId(request);
        log.info("查询用户id:{}最近的信息",userId);
        return messageService.getRecentConversations(userId);
    }
}
