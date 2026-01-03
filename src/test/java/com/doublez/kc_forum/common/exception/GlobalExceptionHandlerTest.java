package com.doublez.kc_forum.common.exception;

import com.doublez.kc_forum.common.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class GlobalExceptionHandlerTest {

//    @Test
//    void applicationExceptionHandler() {
//        try {
//            throw new ApplicationException(Result.failed("TEST"));
//        } catch (ApplicationException e) {
//            System.out.println(e.getMessage());
//        }
//    }

    @Test
    void exceptionHandler()  {
        try {
            throw new Exception("测试用例");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}