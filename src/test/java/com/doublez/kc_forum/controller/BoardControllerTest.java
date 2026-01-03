package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.service.impl.BoardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoardController.class)
class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BoardServiceImpl boardService;

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
    void getAllBoards_Success() throws Exception {
        Board board1 = new Board();
        board1.setId(1L);
        board1.setName("Java");
        
        Board board2 = new Board();
        board2.setId(2L);
        board2.setName("Python");

        List<Board> boards = Arrays.asList(board1, board2);

        when(boardService.selectAllBoards()).thenReturn(boards);

        mockMvc.perform(get("/board/topBoard")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Java"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("Python"));
    }
}
