package com.luyou.live.server;

import com.luyou.live.server.media.LivekitConfig;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
@EnableConfigurationProperties(LivekitConfig.class)
public class LiveServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveServerApplication.class, args);
    }

    /** 清理上次运行残留的在线集合（presence:*），防止重启后在线人数虚高 */
    @Bean
    ApplicationRunner clearPresence(StringRedisTemplate redis) {
        return args -> {
            var keys = redis.keys("presence:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        };
    }
}
