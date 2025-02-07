package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.doublez.kc_forum.mapper.BoardMapper;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.service.IBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BoardServiceImpl implements IBoardService {

    @Autowired
    private BoardMapper boardMapper;

    @Override
    public List<Board> selectBoardsByNum(int num) {
        if(num <= 0 ){
            return null;
        }
        return List.of();
    }

    @Override
    public List<Board> selectAllBoards() {
        return boardMapper.selectList(new LambdaQueryWrapper<Board>().orderByAsc(Board::getSortPriority)
                .eq(Board::getDeleteState,0).eq(Board::getState,0));
    }
}
