package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.service.impl.LikesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LikesController.class)
class LikesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LikesServiceImpl likeService;

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
    void likeArticle_Success() throws Exception {
        doNothing().when(likeService).like(anyLong(), anyLong(), anyString());

        mockMvc.perform(post("/likes/addLike")
                .header("Authorization", "Bearer " + token)
                .param("targetId", "100")
                .param("targetType", "article"))
                .andExpect(status().isOk());

        verify(likeService).like(1L, 100L, "article");
    }

    @Test
    void unlikeArticle_Success() throws Exception {
        doNothing().when(likeService).unlike(anyLong(), anyLong(), anyString());

        mockMvc.perform(post("/likes/unLike")
                .header("Authorization", "Bearer " + token)
                .param("targetId", "100")
                .param("targetType", "article"))
                .andExpect(status().isOk());

        verify(likeService).unlike(1L, 100L, "article");
    }
}
