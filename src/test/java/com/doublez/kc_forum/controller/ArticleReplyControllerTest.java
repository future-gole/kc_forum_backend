package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.ArticleReplyServiceImpl;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArticleReplyController.class)
class ArticleReplyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArticleReplyServiceImpl articleReplyServiceImpl;

    @MockBean
    private UserServiceImpl userService;

    @MockBean
    private ArticleMapper articleMapper;

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
    void createArticleReply_Success() throws Exception {
        ArticleReplyAddRequest request = new ArticleReplyAddRequest();
        request.setArticleId(100L);
        request.setPostUserId(200L);
        request.setContent("This is a reply");
        request.setReplyId(0L);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setState(0); // Not banned
        mockUser.setDeleteState(0);

        when(userService.selectUserInfoById(1L)).thenReturn(mockUser);
        doNothing().when(articleReplyServiceImpl).createArticleReply(any(ArticleReplyAddRequest.class));

        mockMvc.perform(post("/articleReply/createArticleReply")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));
    }

    @Test
    void createArticleReply_UserBanned() throws Exception {
        ArticleReplyAddRequest request = new ArticleReplyAddRequest();
        request.setArticleId(100L);
        request.setPostUserId(200L);
        request.setContent("This is a reply");

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setState(1); // Banned

        when(userService.selectUserInfoById(1L)).thenReturn(mockUser);

        mockMvc.perform(post("/articleReply/createArticleReply")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.FAILED_USER_BANNED.getCode()));
    }
}
