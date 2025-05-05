package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.service.impl.BoardServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/board")
@Tag(name = "板块类",description = "板块相关api")
public class BoardController {
    @Autowired
    private BoardServiceImpl boardService;


    @GetMapping("/topBoard")
    @Operation(summary = "获取所有板块")
    public List<Board> getAllBoards() {
        return boardService.selectAllBoards();
    }
}
