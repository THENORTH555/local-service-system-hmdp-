package com.hmdp.config;


import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

@Configuration
public class CaffeineConfig {

    /**
     * 店铺本地缓存
     * 规则：
     * 1. 最大容量 1000条
     * 2. 写入后过期 5分钟（比Redis短，减少不一致窗口）
     * 3. LoadingCache：get的时候自动加载数据
     */
    @Bean
    public LoadingCache<Long, Shop> shopLocalCache(IShopService shopService) {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats() // 统计命中率，面试加分
                .build(id -> {
                    // 缓存不存在时触发加载，这里只做兜底，正常流程不会走到这里
                    return shopService.getById(id);
                });
    }
}
