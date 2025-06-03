package com.doublez.kc_forum.common.advice;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> businessExceptionHandler(BusinessException e) {
        // Directly use the ResultCode (and its HttpStatus) from the exception
        ResultCode resultCode = e.getResultCode();
        log.warn("BusinessException caught: code={}, httpStatus={}, message='{}'",
                resultCode.getCode(), resultCode.getHttpStatus(), e.getMessage());

        // The errResult in the exception should already be correctly formatted
        return new ResponseEntity<>(e.getErrResult(), resultCode.getHttpStatus());
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Result<?>> systemExceptionHandler(SystemException e) {
        ResultCode resultCode = e.getResultCode();
        log.error("SystemException caught: code={}, httpStatus={}, message='{}'",
                resultCode.getCode(), resultCode.getHttpStatus(), e.getMessage(), e); // Log full stack for system errors

        return new ResponseEntity<>(e.getErrResult(), resultCode.getHttpStatus());
    }


    // Handler for ApplicationException if it can be thrown directly
    // or if other custom exceptions inherit from it but not Business/System
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Result<?>> applicationExceptionHandler(ApplicationException e) {
        ResultCode resultCode = e.getResultCode();
        // This case might occur if ApplicationException is thrown directly
        // without being a BusinessException or SystemException
        if (resultCode == null) {
            // This should ideally not happen if ApplicationException is always constructed with a ResultCode.
            // But as a fallback:
            log.error("ApplicationException caught without a specific ResultCode. Defaulting to ERROR_SERVICES. Message: {}", e.getMessage(), e);
            resultCode = ResultCode.ERROR_SERVICES;
            return new ResponseEntity<>(Result.failed(resultCode, e.getMessage()), resultCode.getHttpStatus());
        }

        log.error("Generic ApplicationException caught: code={}, httpStatus={}, message='{}'",
                resultCode.getCode(), resultCode.getHttpStatus(), e.getMessage(), e);
        return new ResponseEntity<>(e.getErrResult(), resultCode.getHttpStatus());
    }

    /**
     * 处理资源未找到异常 (Spring 6+ for static resources, or if throwExceptionIfNoHandlerFound=true)
     * 如果你希望404也返回统一JSON格式
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> noResourceFoundExceptionHandler(NoResourceFoundException e) {
        log.warn("NoResourceFoundException caught: Resource not found for request URL '{}'", e.getResourcePath());
        ResultCode resultCode = ResultCode.FAILED_NOT_EXISTS; // Specific ResultCode
        Result<?> result = Result.failed(resultCode);
        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }
    //todo 统一风格
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
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}. Root cause: {}", e.getMessage(), e.getMostSpecificCause().getMessage());
        ResultCode resultCode = ResultCode.FAILED_PARAMS_VALIDATE; // Specific ResultCode
        Result<?> result = Result.failed(resultCode, "请求体格式错误或参数无法解析");
        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }

    /**
     * 处理参数校验异常 MethodArgumentNotValidException
     * 通常由 @Valid 注解触发
     */
    //todo 统一风格
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
