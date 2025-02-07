package com.doublez.kc_forum.common.exception;

import com.doublez.kc_forum.common.Result;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@Data
public class ApplicationException extends RuntimeException{
    //异常中持有一个错误信息对象
    @Getter
    private Result errResult;

    public ApplicationException(Result errResult){
        super(errResult.getMessage());
        this.errResult = errResult;
    }
}
