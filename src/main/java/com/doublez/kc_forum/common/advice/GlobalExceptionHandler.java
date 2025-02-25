package com.doublez.kc_forum.common.advice;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局异常处理
 */
@ControllerAdvice
@ResponseBody
@Slf4j
@Data
public class GlobalExceptionHandler {

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
    public Result exceptionHandler(Exception e) throws NoResourceFoundException {
        // 排除资源未找到的异常
        if (e instanceof NoResourceFoundException) {
            throw (NoResourceFoundException) e; // 重新抛出，让 Spring 的默认处理器处理
        }
        e.printStackTrace();//生产环境需要去掉
        log.error(e.getMessage());

        if(e.getMessage() == null || "".equals(e.getMessage())){
            return Result.failed(ResultCode.ERROR_SERVICES);
        }
        //返回异常信息
        return Result.failed(e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result HttpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.error(ResultCode.FAILED_PARAMS_VALIDATE.getMessage() + e.getMessage());

        if(e.getMessage() == null || "".equals(e.getMessage())){
            return Result.failed(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        return Result.failed(e.getMessage());
    }

    //类型转化异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : fieldErrors) {
            errors.put(error.getField(), error.getDefaultMessage()); // 获取 default message
        }
        //用result进行包装，防止ResponseAdvice又包装一遍
        Result<Map<String, String>> result = new Result<>(ResultCode.FAILED_PARAMS_VALIDATE.getCode(), ResultCode.FAILED_PARAMS_VALIDATE.getMessage(), errors);
        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
    }
}
