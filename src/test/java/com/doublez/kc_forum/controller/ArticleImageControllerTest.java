package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.pojo.response.ImageUploadResponseDTO;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.service.impl.ArticleImageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArticleImageController.class)
class ArticleImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArticleImageServiceImpl articleImageService;

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
    void uploadImage_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        ImageUploadResponseDTO response = new ImageUploadResponseDTO();
        response.setUrl("http://example.com/test.jpg");

        when(articleImageService.uploadImage(anyLong(), any(), any())).thenReturn(response);

        mockMvc.perform(multipart("/article/1/images")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("http://example.com/test.jpg"));
    }

    @Test
    void getArticleImages_Success() throws Exception {
        ImageUploadResponseDTO response = new ImageUploadResponseDTO();
        response.setUrl("http://example.com/test.jpg");

        when(articleImageService.getArticleImages(1L)).thenReturn(Collections.singletonList(response));

        mockMvc.perform(get("/article/1/images")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].url").value("http://example.com/test.jpg"));
    }
}
