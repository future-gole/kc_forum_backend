package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.mapper.BoardMapper;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.service.IBoardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class BoardServiceImpl implements IBoardService {

    @Autowired
    private BoardMapper boardMapper;

    //TODO
    @Override
    public List<Board> selectBoardsByNum(int num) {
        if(num <= 0 ){
            return null;
        }
        return List.of();
    }

    @Override
    public Board selectOneBoardById(Long id) {
        if(id <= 0 ){
            return null;
        }
        return boardMapper.selectById(id);
    }
    /**
     * 获取所有板块
     * @return
     */

    @Override
    public List<Board> selectAllBoards() {
        List<Board> boardList = boardMapper.selectList(new LambdaQueryWrapper<Board>().orderByAsc(Board::getSortPriority)
                .eq(Board::getDeleteState,0).eq(Board::getState,0));
        log.info("boardList查询成功:{}", boardList);
        return boardList;
    }

    /**
     * 更新板块数量
     * @param id
     */
    @Override
    public void updateOneArticleCountById(Long id,int increment) {
        if(id == null || id <= 0 ) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 直接更新，利用数据库原子操作避免并发问题
        int rows = boardMapper.update( new LambdaUpdateWrapper<Board>()
                .setSql("article_count = article_count + " + increment) // 确保字段名与数据库一致
                .eq(Board::getId, id)
                .eq(Board::getState,0)//判断是否被禁言
                .eq(Board::getDeleteState, 0)
        );
        if (rows == 0) {
            log.warn("更新用户发帖数量失败, userId: {}", id);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        log.info("板块：文章数量更新");
    }

}
