package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.service.impl.BoardServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/board")
public class BoardController {
    @Autowired
    private BoardServiceImpl boardService;


    @GetMapping("/topBoard")
    public List<Board> getAllBoards() {
        return boardService.selectAllBoards();
    }
}
