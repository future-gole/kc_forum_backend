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
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> applicationExceptionHandler(ApplicationException e) {
        //打印堆栈信息日志
        log.error(e.getMessage(),e);
        if(e.getErrResult() != null){
            return e.getErrResult();
        }
        return Result.failed(ResultCode.ERROR_SERVICES);
    }

    /**
     * 处理资源未找到异常 (Spring 6+ for static resources, or if throwExceptionIfNoHandlerFound=true)
     * 如果你希望404也返回统一JSON格式
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> noResourceFoundExceptionHandler(NoResourceFoundException e) {
        log.warn("NoResourceFoundException caught: Resource not found for request URL '{}'", e.getResourcePath());
        return Result.failed(ResultCode.FAILED_NOT_EXISTS);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> globalExceptionHandler(Exception e) {
        // 对于未被特定处理器捕获的任何其他异常
        log.error("Unhandled Exception caught: {}", e.getMessage(), e);
        // 不应将 e.getMessage() 直接暴露给用户，因为它可能包含敏感信息或技术细节
        // return Result.failed(ResultCode.ERROR_SERVICES);
        return Result.failed(ResultCode.ERROR_SERVICES); // 或者使用ResultCode
    }

    /**
     * 处理HTTP消息不可读异常 HttpMessageNotReadableException
     * 例如，请求体JSON格式错误
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> HttpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("Http message not readable exception: {}",e.getMessage(),e);

        return Result.failed(ResultCode.FAILED_PARAMS_VALIDATE);
    }

    /**
     * 处理参数校验异常 MethodArgumentNotValidException
     * 通常由 @Valid 注解触发
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : fieldErrors) {
            errors.put(error.getField(), error.getDefaultMessage()); // 获取 default message
        }
        // 使用 ResultCode.FAILED_PARAMS_VALIDATE 来提供统一的code和message
        return new Result<>(ResultCode.FAILED_PARAMS_VALIDATE.getCode(), ResultCode.FAILED_PARAMS_VALIDATE.getMessage(), errors);
    }
}
