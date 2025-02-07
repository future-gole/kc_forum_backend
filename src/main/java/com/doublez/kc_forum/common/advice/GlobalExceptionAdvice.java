package com.doublez.kc_forum.common.advice;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常处理
 */
@ControllerAdvice
@ResponseBody
@Slf4j
public class GlobalExceptionAdvice {

    @ExceptionHandler(ApplicationException.class)
    public Result applicationExceptionHandler(ApplicationException e) {
        //打印堆栈信息
        e.printStackTrace();//生产环境需要去掉
        //打印日志
        log.error(e.getMessage());
        if(e.getErrResult() != null){
            return e.getErrResult();
        }
        //非空校验
        if(e.getMessage() == null || "".equals(e.getMessage())){
            return Result.failed(ResultCode.ERROR_SERVICES);
        }
        //返回异常信息
        return Result.failed(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception e) {
        e.printStackTrace();//生产环境需要去掉
        log.error(e.getMessage());

        if(e.getMessage() == null || "".equals(e.getMessage())){
            return Result.failed(ResultCode.ERROR_SERVICES);
        }
        //返回异常信息
        return Result.failed(e.getMessage());
    }
}
