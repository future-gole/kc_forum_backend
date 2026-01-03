package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailServiceImpl emailService;

    @Test
    void sendVerificationCode_Success() throws Exception {
        when(emailService.sendVerificationCode(anyString())).thenReturn(Result.success());

        mockMvc.perform(post("/email/sendVerificationCode")
                .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void verifyEmail_Success() throws Exception {
        mockMvc.perform(post("/email/verifyEmail")
                .param("email", "test@example.com")
                .param("code", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
