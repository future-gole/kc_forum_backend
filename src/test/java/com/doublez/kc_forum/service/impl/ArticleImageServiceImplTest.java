package com.doublez.kc_forum.service.impl;

import com.doublez.kc_forum.common.pojo.response.ImageUploadResponseDTO;
import com.doublez.kc_forum.mapper.ArticleImageMapper;
import com.doublez.kc_forum.model.ArticleImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleImageServiceImplTest {

    @Mock
    private ArticleImageMapper articleImageMapper;

    @InjectMocks
    private ArticleImageServiceImpl articleImageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(articleImageService, "basePath", tempDir.toString());
        ReflectionTestUtils.setField(articleImageService, "baseUrl", "http://localhost:8080");
        // Manually set baseMapper for ServiceImpl
        // Use the field name "baseMapper" which is protected in ServiceImpl
        ReflectionTestUtils.setField(articleImageService, "baseMapper", articleImageMapper);
    }

    @Test
    void uploadImage_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        when(articleImageMapper.insert(any(ArticleImage.class))).thenReturn(1);

        ImageUploadResponseDTO response = articleImageService.uploadImage(1L, file, 0);

        assertNotNull(response);
        assertEquals("test.jpg", response.getFileName());
        assertTrue(response.getFullUrl().startsWith("http://localhost:8080/images/articles/"));
        
        verify(articleImageMapper).insert(any(ArticleImage.class));
    }
}
