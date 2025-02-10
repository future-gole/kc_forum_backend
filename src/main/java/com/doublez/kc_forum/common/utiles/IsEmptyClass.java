package com.doublez.kc_forum.common.utiles;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.model.User;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IsEmptyClass {

    public static<T> void Empty(T source,ResultCode resultCode,Long id)  {
        if(source == null){
            log.error("{} 资源id:{}", resultCode.getMessage(), id);
            throw new ApplicationException(Result.failed(resultCode));
        }
    }
}
