package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.response.RecentConversationsResponse;
import com.doublez.kc_forum.mapper.MessageMapper;
import com.doublez.kc_forum.model.Message;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IMessageService;
import com.doublez.kc_forum.service.IUserService;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class MessageServiceImpl implements IMessageService {
    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private IUserService userServiceImpl;

    @Override
    @Transactional
    public void create(Message message) {
        //1. 校验信息是否合法
        if(message == null || message.getReceiveUserId() == null
            || message.getPostUserId() == null || StringUtils.isEmpty(message.getContent())){
            log.warn(ResultCode.ERROR_MESSAGE_NOT_VALID.toString());
            throw new BusinessException(ResultCode.ERROR_MESSAGE_NOT_VALID);
        }
        //2. 校验用户是否存在
        User receiveUser = userServiceImpl.selectUserInfoById(message.getReceiveUserId());
        if(receiveUser == null){
            log.warn(ResultCode.FAILED_USER_NOT_EXISTS.toString());
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        User postUser = userServiceImpl.selectUserInfoById(message.getPostUserId());
        if(postUser == null){
            log.warn(ResultCode.FAILED_USER_NOT_EXISTS.toString());
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //3. 插入message
        int insert = messageMapper.insert(message);
        if(insert != 1){
            log.error(ResultCode.FAILED_MESSAGE_INSERT.toString());
            throw new SystemException(ResultCode.FAILED_MESSAGE_INSERT);
        }
    }

    @Override
    public long selectUnreadCount(Long userId) {
        if(userId == null || userId <= 0){
            log.warn(ResultCode.FAILED_PARAMS_VALIDATE.toString());
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
            .eq(Message::getReceiveUserId, userId)
            .eq(Message::getState, 0));
    }

    @Override
    @Transactional
    public List<Message> selectAllMessageByPostUserID(Long receiveUserId, Long PostUserId) {
        if (receiveUserId == null || PostUserId == null
                || receiveUserId <= 0 || PostUserId <= 0) {
            log.warn(ResultCode.FAILED_PARAMS_VALIDATE.toString());
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        //1. 查询消息
        LambdaQueryWrapper<Message> queryWrapper = new LambdaQueryWrapper<>();

        // SELECT id,receive_user_id,post_user_id,state,content,create_time
        queryWrapper.select(Message::getId, Message::getReceiveUserId, Message::getPostUserId,
                Message::getState, Message::getContent, Message::getCreateTime);

        // WHERE (((receive_user_id = ? AND post_user_id = ?) or(receive_user_id = ? AND post_user_id = ?)))
        queryWrapper.nested(i -> i.eq(Message::getReceiveUserId, receiveUserId).eq(Message::getPostUserId, PostUserId))
                .or()
                .nested(i -> i.eq(Message::getReceiveUserId, PostUserId).eq(Message::getPostUserId, receiveUserId));

        // ORDER BY create_time ASC
        queryWrapper.orderByAsc(Message::getCreateTime);

        List<Message> messages = messageMapper.selectList(queryWrapper);
        //2. 更新消息状态
        // 只有当查询到消息时才执行更新操作
        if (!messages.isEmpty()) {
            LambdaUpdateWrapper<Message> updateWrapper = new LambdaUpdateWrapper<>();

            // 设置要更新的字段及其值
            // 创建一个 Message 对象，只设置需要更新的字段
            Message updateMessage = new Message();
            updateMessage.setState((byte)1); // 将 state 设置为 1 (已读)
            // 只更新当前用户的未读信息
            updateWrapper.eq(Message::getReceiveUserId, receiveUserId)
                    .eq(Message::getPostUserId, PostUserId)
                    .eq(Message::getState, 0);
            // 执行更新操作
            // update(entity, updateWrapper) 方法会根据 updateWrapper 的条件更新 entity 中非空的字段
            messageMapper.update(updateMessage, updateWrapper);
        }

        return messages;
    }

    public List<RecentConversationsResponse> getRecentConversations(Long userId) {
        if(userId == null || userId < 1){
            log.warn(ResultCode.FAILED_PARAMS_VALIDATE.toString());
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        // 调用 XML 中定义的查询方法
        return messageMapper.getRecentConversations(userId);
    }
}

