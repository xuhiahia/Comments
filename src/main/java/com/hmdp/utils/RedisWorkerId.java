package com.hmdp.utils;

import ch.qos.logback.classic.layout.TTLLLayout;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorkerId {
    private static final long BEGAIN_TIME= 1609459200l;
    private static final long COUNT_BASE= 32L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public  long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTime = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTime - BEGAIN_TIME;
        //生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timeStamp<<COUNT_BASE | increment;
    }
}
