package com.doublez.kc_forum.common.utiles;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AssertUtil {

    /**
     * 判断资源是否为空
     * @param source
     * @param resultCode
     * @param id
     * @param <T>
     */
    public static<T> void checkClassNotNull(T source, ResultCode resultCode, Long id)  {
        if(source == null){
            log.error("{} 资源id:{}", resultCode.getMessage(), id);
            throw new BusinessException(resultCode);
        }
    }
}
