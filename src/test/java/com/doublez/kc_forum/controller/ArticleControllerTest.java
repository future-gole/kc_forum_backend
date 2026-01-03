package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.pojo.request.ArticleAddRequest;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.ArticleServiceImpl;
import com.doublez.kc_forum.service.impl.BoardServiceImpl;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArticleServiceImpl articleService;

    @MockBean
    private UserServiceImpl userService;

    @MockBean
    private BoardServiceImpl boardService;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        // Initialize JwtUtil with a test secret
        JwtUtil.init("dGhpcyBpcyBhIHRlc3Qgc2VjcmV0IGtleSBmb3IgdGVzdGluZyBwdXJwb3Nlcw==", 3600000);

        // Generate a valid token
        Map<String, Object> claims = new HashMap<>();
        claims.put("Id", 1L);
        claims.put("email", "test@example.com");
        token = JwtUtil.genToken(claims);
    }

    @Test
    void create_Success() throws Exception {
        ArticleAddRequest request = new ArticleAddRequest();
        request.setTitle("Test Article");
        request.setContent("This is a test content");
        request.setBoardId(1L);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setState(0); // Not banned
        mockUser.setDeleteState(0);

        Board mockBoard = new Board();
        mockBoard.setId(1L);
        mockBoard.setState((byte) 0); // Normal
        mockBoard.setDeleteState((byte) 0);

        when(userService.selectUserInfoById(1L)).thenReturn(mockUser);
        when(boardService.selectOneBoardById(1L)).thenReturn(mockBoard);
        // articleService.createArticle returns void, so doNothing is default for mocks

        mockMvc.perform(post("/article/create")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));
    }

    @Test
    void create_UserBanned() throws Exception {
        ArticleAddRequest request = new ArticleAddRequest();
        request.setTitle("Test Article");
        request.setContent("This is a test content");
        request.setBoardId(1L);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setState(1); // Banned

        when(userService.selectUserInfoById(1L)).thenReturn(mockUser);

        mockMvc.perform(post("/article/create")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()) // GlobalExceptionHandler returns 401 for banned user
                .andExpect(jsonPath("$.code").value(ResultCode.FAILED_USER_BANNED.getCode()));
    }
}
