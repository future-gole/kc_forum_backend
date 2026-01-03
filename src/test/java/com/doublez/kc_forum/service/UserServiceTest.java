package com.doublez.kc_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.mapper.UserMapper;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.EmailServiceImpl;
import com.doublez.kc_forum.service.impl.RefreshTokenService;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private EmailServiceImpl emailServiceImpl;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        // Set values for @Value fields if necessary
        ReflectionTestUtils.setField(userService, "refreshTokenExpirationMillis", 3600000L);
        // Initialize JwtUtil
        JwtUtil.init("dGhpcyBpcyBhIHRlc3Qgc2VjcmV0IGtleSBmb3IgdGVzdGluZyBwdXJwb3Nlcw==", 3600000);
    }

    @Test
    void createNormalUser_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setCode("123456");
        request.setUserName("testuser");

        // Mock email verification success (boolean method)
        when(emailServiceImpl.verifyEmail(anyString(), anyString())).thenReturn(true);

        // Mock user insertion success
        when(userMapper.insert(any(User.class))).thenReturn(1);

        Result<?> result = userService.createNormalUser(request);

        assertNotNull(result);
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(emailServiceImpl).verifyEmail("test@example.com", "123456");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void createNormalUser_EmailVerificationFailed() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setCode("wrongcode");

        // Mock email verification failure
        doThrow(new BusinessException(ResultCode.FAILED_USER_BANNED)) // Just picking an exception
                .when(emailServiceImpl).verifyEmail(anyString(), anyString());

        assertThrows(BusinessException.class, () -> userService.createNormalUser(request));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void login_Success() {
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setPassword(SecurityUtil.encrypt("password123"));
        mockUser.setDeleteState(0);

        // Mock user retrieval
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        UserLoginResponse loginResponse = userService.login(request, response);

        assertNotNull(loginResponse);
        assertEquals(1L, loginResponse.getUserId());
        assertNotNull(loginResponse.getAuthorization());
        
        // Verify refresh token creation
        verify(refreshTokenService).createRefreshToken(anyString());
        // Verify cookie added
        verify(response).addCookie(any());
    }

    @Test
    void login_UserNotFound() {
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail("nonexistent@example.com");
        request.setPassword("password123");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(request, response));
        assertEquals(ResultCode.FAILED_LOGIN.getCode(), exception.getResultCode().getCode());
    }

    @Test
    void login_WrongPassword() {
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpassword");

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setPassword(SecurityUtil.encrypt("password123"));
        mockUser.setDeleteState(0);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(request, response));
        assertEquals(ResultCode.FAILED_LOGIN.getCode(), exception.getResultCode().getCode());
    }

    @Test
    void selectUserInfoByUserEmail_Success() {
        String email = "test@example.com";
        User mockUser = new User();
        mockUser.setEmail(email);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        User result = userService.selectUserInfoByUserEmail(email);

        assertNotNull(result);
        assertEquals(email, result.getEmail());
    }

    @Test
    void selectUserInfoByUserEmail_NotFound() {
        String email = "nonexistent@example.com";
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.selectUserInfoByUserEmail(email));
    }

    @Test
    void selectUserInfoById_Success() {
        Long userId = 1L;
        User mockUser = new User();
        mockUser.setId(userId);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        User result = userService.selectUserInfoById(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
    }

    @Test
    void selectUserInfoById_NotFound() {
        Long userId = 1L;
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.selectUserInfoById(userId));
    }

    @Test
    void updateOneArticleCountById_Success() {
        Long userId = 1L;
        int increment = 1;
        // Assuming single argument update based on BoardServiceImplTest experience
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        userService.updateOneArticleCountById(userId, increment);

        verify(userMapper).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateOneArticleCountById_Failure() {
        Long userId = 1L;
        int increment = 1;
        when(userMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(SystemException.class, () -> userService.updateOneArticleCountById(userId, increment));
    }
}
