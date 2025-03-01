package com.doublez.kc_forum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling//启动定时删除二维码任务
public class KcForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(KcForumApplication.class, args);
    }

}
