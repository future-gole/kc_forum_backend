package com.doublez.kc_forum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class KcForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(KcForumApplication.class, args);
    }

}
