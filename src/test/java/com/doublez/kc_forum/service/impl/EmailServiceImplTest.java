package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.mapper.UserMapper;
import com.doublez.kc_forum.model.User;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private EmailServiceImpl emailService;

    @Test
    void sendVerificationCode_Success() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@example.com");
        String email = "test@example.com";
        
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // Email not registered
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Use real MimeMessage with a real Session
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Result<?> result = emailService.sendVerificationCode(email);

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(valueOperations).set(anyString(), anyString(), eq(10L), eq(TimeUnit.MINUTES));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendVerificationCode_EmailAlreadyRegistered() {
        String email = "test@example.com";
        
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new User()); // Email registered

        Result<?> result = emailService.sendVerificationCode(email);

        assertEquals(ResultCode.ERROR_EMAIL_ALREADY_REGISTERED.getCode(), result.getCode());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void verifyEmail_Success() {
        String email = "test@example.com";
        String code = "123456";
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(code);

        boolean result = emailService.verifyEmail(email, code);

        assertTrue(result);
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void verifyEmail_InvalidCode() {
        String email = "test@example.com";
        String code = "123456";
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("654321"); // Different code

        assertThrows(BusinessException.class, () -> emailService.verifyEmail(email, code));
    }

    @Test
    void verifyEmail_ExpiredCode() {
        String email = "test@example.com";
        String code = "123456";
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // Code expired

        assertThrows(BusinessException.class, () -> emailService.verifyEmail(email, code));
    }
}
