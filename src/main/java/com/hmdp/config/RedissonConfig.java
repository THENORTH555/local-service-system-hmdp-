package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName:RedissonConfig
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/10/20 15:11
 * @Version 1.0
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        // 创建配置
        Config config = new Config();
        // 设置Redis地址, 密码为123456
        config.useSingleServer().setAddress("redis://192.168.220.128:6379").setPassword("123456");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
