package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.pojo.request.MessageRequest;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.Message;
import com.doublez.kc_forum.service.impl.MessageServiceImpl;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageServiceImpl messageService;

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
    void sendMessage_Success() throws Exception {
        MessageRequest request = new MessageRequest(2L);
        request.setContent("Hello World");

        doNothing().when(messageService).create(any(Message.class));

        mockMvc.perform(post("/message/send")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));

        verify(messageService).create(any(Message.class));
    }

    @Test
    void sendMessage_ToSelf_Fail() throws Exception {
        MessageRequest request = new MessageRequest(1L); // Sending to self (userId=1 from token)
        request.setContent("Hello Self");

        mockMvc.perform(post("/message/send")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // GlobalExceptionHandler maps BusinessException to 400/500 depending on config, usually 200 with error code or 400
                .andExpect(jsonPath("$.code").value(ResultCode.ERROR_MESSAGE_NOT_VALID.getCode()));
    }
}
