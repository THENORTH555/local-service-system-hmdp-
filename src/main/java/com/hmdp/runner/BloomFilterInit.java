package com.hmdp.runner;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ClassName:BloomFilterInit
 * Description:
 *
 * @Author 何永琪
 * @Create 2026/5/18 17:26
 * @Version 1.0
 */
@Component
public class BloomFilterInit  {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private IShopService shopService;

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private TaskExecutor taskExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    private static final String BLOOM_SHOP = "bloom:shop";
    //这个postconstruct注解可以确保布隆过滤器初始化方法在 Bean 前执行
    @PostConstruct
    public void initBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(BLOOM_SHOP);
        //布隆过滤器用tryinit方法初始化,前面设置的是存入的数据最大总量，后面的是误判率
        bloomFilter.tryInit(10000L, 0.01);
        List<Shop> shopList = shopService.list();

        //以下这段是要在mapper中写sql的不优雅的写法
//        List<Long> ids = shopMapper.selectAllShopIds();
////流式处理，由于布隆过滤器一次只能接受一个id，所以不能把ids批量一次性加到里面去，只能通过流式处理一个个加进去
//        ids.stream().forEach(bloomFilter::add);
        //最优雅的写法，直接用mp中service继承iservice接口后获得的方法来操作数据库,.map()不是转表，而是流里的转换数据方法
        shopService.list().stream()
                .map(Shop::getId)
                .forEach(bloomFilter::add);

        System.out.println("布隆处理器初始化完成");

        taskExecutor.execute(() -> {
            try {
                List<Shop> hotShopList = shopService.list().subList(0, Math.min(100, shopList.size()));
                for (Shop shop : hotShopList) {
                    String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
                    stringRedisTemplate.opsForValue()
                            .set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                }
                System.out.println("✅ 店铺缓存预热完成，已加载" + hotShopList.size() + "个热门店铺");
            } catch (Exception e) {
                System.err.println("❌ 缓存预热异常：" + e.getMessage());
                e.printStackTrace();
            }
        });











    }
}
