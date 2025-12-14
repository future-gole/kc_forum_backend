package com.doublez.kc_forum.common.exception;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;



@Getter
public class ApplicationException extends RuntimeException{
    //异常中持有一个错误信息对象
    private final ResultCode resultCode;
    private final Result<?> errResult;

    public ApplicationException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.errResult = Result.failed(resultCode);
    }

    public ApplicationException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.resultCode = resultCode;
        this.errResult = Result.failed(resultCode, customMessage);
    }
}
