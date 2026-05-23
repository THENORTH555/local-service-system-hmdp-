package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import io.lettuce.core.GeoArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * ClassName:CacheClient
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/10/12 9:21
 * @Version 1.0
 */
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    //构造器
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //缓存数据,并且序列化
    public void set(String key, Object value, Long time ,TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }
    //缓存数据,并且序列化,为其设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time ,TimeUnit timeUnit) {
        //创建一个RedisData对象，并用传入的数据填充，设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //将该redisData对象序列化成JSON字符串，并保存在Redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    //由于是工具类，故这里用泛型接收不确定的数据类型的对象和id
    //工具类中的解决缓存穿透的方法
    public <R, ID> R quaryWithPassThrough(String keyprifix, Class<R> type, ID id , Function<ID, R> dbFallback , Long time ,TimeUnit timeUnit){
        String key = keyprifix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //查询redis中是否存在对应缓存
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        //判断命中的是否为空值
        if (json != null){
            return null;
        }
        //用传入的方法获取数据，因为不确定数据类型，所以方法也不确定，只能接收传入的方法，返回数据
        R r = dbFallback.apply(id);

        if (r == null){
            //把空值写入redis中,这里是预防缓存穿透添加的一层保险
            stringRedisTemplate.opsForValue().set(key, "",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        //将查到的对象序列化并写入redis中，即添加缓存
        this.set(key, r, time, timeUnit);
        return r;
    }
    //解决缓存击穿的方法
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//创建线程池对象并赋值给CACHE_REBUILD_EXECUTOR常量
    public <R, ID> R quaryWithLogicalExpire(String keyprifix,String lockprifix, Class<R> type, ID id,Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyprifix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //查询redis中是否存在对应缓存
        //若不存在，则先查数据库，数据库没有再打出null
        if (StrUtil.isBlank(json)) {
            R r = dbFallback.apply(id);
            if (r == null) {
                //设置空值
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }else {
                //把查到的放到缓存中去，进行序列化
                this.setWithLogicalExpire(key,r,time,timeUnit);
            }

        }
        //1命中，需要先判断过期时间，因为用逻辑过期方法不需要考虑缓存穿透问题，所以不需要存空白值
        com.hmdp.entity.RedisData redisData = JSONUtil.toBean(json, com.hmdp.entity.RedisData.class);
        R  r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime islate = redisData.getExpireTime();
        if (islate.isAfter(LocalDateTime.now())) {
            //未过期，直接返回缓存数据,需要进行序列化
            return r;
        }
        //3,缓存过期，需要缓存重建
        String lock = lockprifix + id;
        boolean flag = tryLock(lock);
        //获取锁失败，返回错误
        if (!flag) {
            return quaryWithLogicalExpire(keyprifix, lockprifix, type, id, dbFallback, time, timeUnit);
        }
        //获取锁成功，从线程池中调用线程，缓存重建，向CACHE_REBUILD_EXECUTOR线程池对象中提交任务
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                R newR = dbFallback.apply(id);
                this.setWithLogicalExpire(key, newR, time, timeUnit);


            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            finally {
                unLock(lock);
            }
        });

        //启动线程并返回旧数据
        return r;
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
}
