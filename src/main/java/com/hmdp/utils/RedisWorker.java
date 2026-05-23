package com.hmdp.utils;

import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ClassName:RedisWorker
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/10/12 11:10
 * @Version 1.0
 */
@Data
@Component
public class RedisWorker {
    //时间戳的起始时间，即当前时间
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    private StringRedisTemplate stringRedisTemplate;

    public RedisWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //全局唯一id生成器
    public long nextId(String key) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //通过stringRedisTemplate.opsForValue().increment()方法对指定key进行自增操作，key的格式为"icr:业务标识:日期"
        long count = stringRedisTemplate.opsForValue().increment("icr:" + key + ":" + date);
        //3.拼接并返回

        //假设：
        //timestamp = 1000 (二进制: 1111101000)
        //count = 5 (二进制: 101)
        //COUNT_BITS = 32
        // 1111101000 << 32
        // 结果：00000011 11101000 00000000 00000000 00000000 00000000 00000000 00000000
        // 十进制：4294967296000
        //将时间戳左移32位，再与序列号进行或运算，得到最终的id，Java中的long类型正好是64位
        //64位能够提供足够大的数值范围，满足大部分业务需求
        return timestamp << COUNT_BITS | count;
        // 00000011 11101000 00000000 00000000 00000000 00000000 00000000 00000000
        // | 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000101
        // --------------------------------------------------------------------------------
        // 1111101000000000000000000000000000000000000000000000000000000101
        // 十进制：4294967296005



    }

}
