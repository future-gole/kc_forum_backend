package com.doublez.kc_forum.common.utiles;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

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

    // 类型转化抽取出来的通用方法
    public static <Source, Target> Target copyProperties(Source source, Class<Target> targetClass) {
        try {
            Target target = targetClass.getDeclaredConstructor().newInstance(); // 使用反射创建实例
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            log.error("类型转换失败: {} -> {}", source.getClass().getName(), targetClass.getName(), e);
            throw new SystemException(ResultCode.ERROR_TYPE_CHANGE);
        }
    }
}
