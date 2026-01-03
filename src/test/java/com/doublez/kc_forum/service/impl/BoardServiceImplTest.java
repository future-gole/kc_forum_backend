package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.mapper.BoardMapper;
import com.doublez.kc_forum.model.Board;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.data.redis.repositories.enabled=false")
class BoardServiceImplTest {

    @Autowired
    private BoardServiceImpl boardService;

    @MockBean
    private BoardMapper boardMapper;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @MockBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void selectAllBoards_CacheHit() throws JsonProcessingException {
        // Arrange
        String key = "boards:list:all";
        List<Board> expectedBoards = new ArrayList<>();
        Board board = new Board();
        board.setId(1L);
        board.setName("Test Board");
        expectedBoards.add(board);
        String json = objectMapper.writeValueAsString(expectedBoards);

        when(valueOperations.get(key)).thenReturn(json);

        // Act
        List<Board> result = boardService.selectAllBoards();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Board", result.get(0).getName());
        verify(boardMapper, never()).selectList(any());
    }

    @Test
    void selectAllBoards_CacheMiss() throws JsonProcessingException {
        // Arrange
        String key = "boards:list:all";
        when(valueOperations.get(key)).thenReturn(null);

        List<Board> dbBoards = new ArrayList<>();
        Board board = new Board();
        board.setId(1L);
        board.setName("DB Board");
        dbBoards.add(board);

        when(boardMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(dbBoards);

        // Act
        List<Board> result = boardService.selectAllBoards();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("DB Board", result.get(0).getName());
        verify(valueOperations).set(eq(key), anyString());
    }

    @Test
    void updateOneArticleCountById_Success() {
        // Arrange
        Long boardId = 1L;
        int increment = 1;
        // Try single argument mock first, as source code seems to use single arg
        when(boardMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        // Act
        boardService.updateOneArticleCountById(boardId, increment);

        // Assert
        verify(boardMapper).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateOneArticleCountById_InvalidId() {
        // Arrange
        Long boardId = -1L;
        int increment = 1;

        // Act & Assert
        assertThrows(BusinessException.class, () -> boardService.updateOneArticleCountById(boardId, increment));
        verify(boardMapper, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void selectOneBoardById_Success() {
        // Arrange
        Long boardId = 1L;
        Board mockBoard = new Board();
        mockBoard.setId(boardId);
        mockBoard.setName("Test Board");
        when(boardMapper.selectById(boardId)).thenReturn(mockBoard);

        // Act
        Board result = boardService.selectOneBoardById(boardId);

        // Assert
        assertNotNull(result);
        assertEquals(boardId, result.getId());
        assertEquals("Test Board", result.getName());
    }

    @Test
    void selectOneBoardById_InvalidId() {
        // Act
        Board result = boardService.selectOneBoardById(0L);

        // Assert
        assertNull(result);
        verify(boardMapper, never()).selectById(anyLong());
    }

    @Test
    void selectBoardsByNum_ReturnsEmpty() {
        // Act
        List<Board> result = boardService.selectBoardsByNum(5);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void selectBoardsByNum_InvalidNum() {
        // Act
        List<Board> result = boardService.selectBoardsByNum(0);

        // Assert
        assertNull(result);
    }
}
