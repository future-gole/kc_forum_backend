package com.doublez.kc_forum.common.exception;

import com.doublez.kc_forum.common.ResultCode;

public class BusinessException extends ApplicationException{
    public BusinessException(ResultCode resultCode) {
        super(resultCode);
    }
    public BusinessException(ResultCode resultCode, String customMessage) {
        super(resultCode, customMessage);
    }
}
