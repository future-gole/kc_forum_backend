package com.doublez.kc_forum.service;

import com.doublez.kc_forum.model.Board;

import java.util.List;

public interface IBoardService {
    //查询前N个正常的板块
    List<Board> selectBoardsByNum(int num);
    //查询所有正常的板块
    List<Board> selectAllBoards();

    /**
     * 更新板块发帖数目
     * @param id
     */
    void updateOneArticleCountById(Long id, String sql);

    Board selectOneBoardById(Long id);
}
