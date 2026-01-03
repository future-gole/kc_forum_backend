package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.UserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserServiceImpl userService;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        JwtUtil.init("dGhpcyBpcyBhIHRlc3Qgc2VjcmV0IGtleSBmb3IgdGVzdGluZyBwdXJwb3Nlcw==", 3600000);
        Map<String, Object> claims = new HashMap<>();
        claims.put("Id", 1L);
        claims.put("email", "test@example.com");
        token = JwtUtil.genToken(claims);
    }

    @Test
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUserName("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setRepeatPassword("password123");
        request.setCode("123456");
        request.setNickName("TestUser");

        when(userService.createNormalUser(any(RegisterRequest.class))).thenReturn(Result.success());

        mockMvc.perform(post("/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void login_Success() throws Exception {
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        UserLoginResponse response = new UserLoginResponse();
        response.setAuthorization("mock-token");
        response.setUserId(1L);

        when(userService.login(any(UserLoginRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorization").value("mock-token"));
    }

    @Test
    void getUserInfo_WithId_Success() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setUserName("otheruser");

        when(userService.selectUserInfoById(2L)).thenReturn(user);

        mockMvc.perform(get("/user/info")
                .header("Authorization", "Bearer " + token)
                .param("id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.userName").value("otheruser"));
    }

    @Test
    void getUserInfo_Current_Success() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUserName("currentuser");

        // When id is null, controller calls JwtUtil.getUserId(request) which returns 1L from token
        when(userService.selectUserInfoById(1L)).thenReturn(user);

        mockMvc.perform(get("/user/info")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.userName").value("currentuser"));
    }
}
