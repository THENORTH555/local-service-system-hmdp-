package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.runner.BloomFilterInit;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedissonClient redissonClient;
    @Override
    public Result querybyID(Long id) {
        //解决缓存穿透走quaryWithPassThrough方法                                                      //this::getById是id2->getbyId(id2)的美化语句，这里主要是得传入一个能进行根据id查询数据库的函数，返回数据
//        Shop shop = cacheClient.quaryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, Shop.class, id, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //解决缓存击穿走互斥锁方法
        //Shop shop = quaryWithMutex(id);

//        Shop shop = cacheClient.quaryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,RedisConstants.LOCK_SHOP_KEY, Shop.class, id,this::getById,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //布隆过滤器结合逻辑过期时间解决缓存穿透，布隆过滤器说不存在一定不存在，即数据库里也没有，布隆过滤器说存在不一定存在，走逻辑过期时间判断
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("bloom:shop");
        if (!bloomFilter.contains(id)){
            return Result.fail("店铺不存在");
        }
        //走逻辑过期时间查询缓存
        Shop shop = cacheClient.quaryWithLogicalExpire
                (RedisConstants.CACHE_SHOP_KEY,RedisConstants.LOCK_SHOP_KEY, Shop.class, id,this::getById,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        // ========== 3. 压测环境改成直接查数据库 ==========
//        Shop shop = this.getById(id);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    //创一个线程池，不要自己调用
//    关键要素说明：
//    static修饰符：
//    确保整个应用中只有一个线程池实例
//    类加载时初始化，所有实例共享同一个线程池
//    final修饰符：
//    保证线程池引用不可变
//    避免意外重新赋值导致的问题
//    private访问修饰符：
//    限制线程池只能在当前类内部使用
//    提供良好的封装性和安全性
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//创建线程池对象并赋值给CACHE_REBUILD_EXECUTOR常量
    public Shop quaryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY;
        String shopjson = stringRedisTemplate.opsForValue().get(key + id);
        //查询redis中是否存在对应缓存
        //若不存在，则返回null
        if (StrUtil.isBlank(shopjson)) {
            return null;
        }
        //1命中，需要先判断过期时间，因为用逻辑过期方法不需要考虑缓存穿透问题，所以不需要存空白值
        RedisData redisData = JSONUtil.toBean(shopjson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime islate = redisData.getExpireTime();
        if (islate.isAfter(LocalDateTime.now())) {
            //未过期，直接返回缓存数据,需要进行序列化
            return shop;
        }
        //3,缓存过期，需要缓存重建
        String lock = RedisConstants.LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lock);
        //获取锁失败，返回错误
        if (!flag) {
            return quaryWithLogicalExpire(id);
        }
        //获取锁成功，从线程池中调用线程，缓存重建，向CACHE_REBUILD_EXECUTOR线程池对象中提交任务
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                saveShop2Redis(id, 20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            finally {
                unLock(lock);
            }
        });

        //启动线程并返回旧数据
        return shop; 
    }
    public Shop quaryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY;
        String shopjson = stringRedisTemplate.opsForValue().get(key + id);
        //查询redis中是否存在对应缓存
        if (StrUtil.isNotBlank(shopjson)) {
            //用jsonutil中的tobean方法把json转为shop对象
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return shop;
        }
        //判断命中的是否为空值
        if (shopjson != null) {
            return null;
        }
        //获取互斥锁并判断是否成功
        String lock = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean flag = tryLock(lock);
            //获取失败，休眠并等待，重新查缓存看是否被人更新缓存了，如果被更新了，则返回更新后的缓存
            if (!flag) {
                Thread.sleep(50);
                return quaryWithMutex(id);
            }
            //获取锁成功，进行缓存重建
            shop = getById(id);
            //不存在，返回错误，建立空值
            if (shop == null) {
                //把空值写入redis中,这里是预防缓存穿透添加的一层保险，这也是更新了缓存
                stringRedisTemplate.opsForValue().set(key + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //将shop对象序列化并写入redis中，即添加缓存
            stringRedisTemplate.opsForValue().set(key + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //thread.sleep方法声明会抛出线程中断异常，必须进行处理
        }catch (InterruptedException e){
           throw new RuntimeException(e);
        }finally {
            unLock(lock);

        }

        return shop;
    }

    //缓存穿透解决方法
    public Shop quaryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY;
        String shopjson = stringRedisTemplate.opsForValue().get(key + id);
        //查询redis中是否存在对应缓存
        if (StrUtil.isNotBlank(shopjson)){
            //用jsonutil中的tobean方法把json转为shop对象
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return shop;
        }
        //判断命中的是否为空值
        if (shopjson != null){
            return null;
        }
        Shop shop = getById(id);
        if (shop == null){
            //把空值写入redis中,这里是预防缓存穿透添加的一层保险
            stringRedisTemplate.opsForValue().set(key + id, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        //将shop对象序列化并写入redis中，即添加缓存
        stringRedisTemplate.opsForValue().set(key + id,JSONUtil.toJsonStr(shop) ,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
    
    
    
    //获取锁方法
    private boolean tryLock(String key) {
        //获取锁
     Boolean flag =  stringRedisTemplate.opsForValue().setIfAbsent(key, "1",10,TimeUnit.SECONDS);
     return BooleanUtil.isTrue(flag);
    }
    //释放锁方法
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
    //封装逻辑过期时间的save方法，这个逻辑过期时间由管理端传入，可控制的
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result updatebyid(Shop shop) {
        updateById(shop);
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //总逻辑，分页查询，每页查询5个georesult封装成georesult集合，将georesult取出后解析出传来的shopid和distance，然后用shopid去数据库查shop对象，将shop对象补齐取到的distance封装好，返回给前端



        //判断是否传了坐标，没传就直接查分页了
        if (x == null || y == null){
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_BATCH_SIZE));
            return Result.ok(page.getRecords());
        }
        //获取分页数据
        int from = (current - 1) * DEFAULT_BATCH_SIZE;
        int end = current * DEFAULT_BATCH_SIZE;
        //查询缓存
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        //解析出id
        if (results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        if (list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        list.stream().skip(from).forEach(result -> {
            //获取店铺id
            String shopid = result.getContent().getName();
            ids.add(Long.valueOf(shopid));
            //获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopid, distance);

        });
        //根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        //用拿到的ids用strutil为每个id中间加逗号之后拼接成查询语句，然后查询出来所有的店铺后用shops列表接收，然后用for循环遍历shops列表，将距离赋给shop对象，最后返回给前端
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回分页数据
        return Result.ok(shops);
        
    }
}
