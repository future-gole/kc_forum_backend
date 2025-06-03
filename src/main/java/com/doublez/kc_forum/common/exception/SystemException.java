package com.doublez.kc_forum.common.exception;

import com.doublez.kc_forum.common.ResultCode;

public class SystemException extends ApplicationException{
    public SystemException(ResultCode resultCode) {
        super(resultCode);
    }
    public SystemException(ResultCode resultCode, String customMessage) {
        super(resultCode, customMessage);
    }
}
