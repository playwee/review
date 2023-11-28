package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 当前时间
     */
    private static final long BEGIN_TIMESTAMP=1672531200L;
    /**
     * 序列号位数
     */
    private static final long COUNT_BITS=32L;
    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now=LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond-BEGIN_TIMESTAMP;
        //2.生成序列号
        //获取天
        String date =now.format(DateTimeFormatter.ofPattern("yyyy:mm:dd"));
        /**
         * 不能用同一个id
         * 可以按天来算，一天下单的量可以直接计算
         * 新的一天不会导致空指针，自动从0开始
         */
        //自增长
        long count=stringRedisTemplate.opsForValue().increment("icr"+keyPrefix+":"+date);
        //3.拼接并返回
        return timestamp<<COUNT_BITS| count;
    }
    public static void main(String[] args){
        LocalDateTime time=LocalDateTime.of(2023,1,1,0,0,0);
        long second= time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second="+second);
    }
}
