package com.luyou.live.server;

import com.luyou.live.server.media.LivekitConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LivekitConfig.class)
public class LiveServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveServerApplication.class, args);
    }
}
