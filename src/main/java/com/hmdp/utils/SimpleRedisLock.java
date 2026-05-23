package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * ClassName:SimpleRedisLock
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/10/18 17:14
 * @Version 1.0
 */
public class SimpleRedisLock implements Ilock{


    private String name;
    private StringRedisTemplate stringRedisTemplate;


    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
        //锁的key的不能写死，得用传入值进行拼接
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) +"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //定义一个脚本类的实体并且用静态代码块为其初始化
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //设置其对应的脚本文件路径
        //ClassPathResource：明确来自 Spring 框架（org.springframework.core.io.ClassPathResource），是 Spring 核心 IO 模块中用于访问类路径资源的工具类。
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //设置返回值类型为Long
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    //过期时间由传入值控制
    public boolean tryLock(long timeoutSec) {
        //获取当前线程id
        String clientId = ID_PREFIX + Thread.currentThread().getId() + "";
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, clientId, timeoutSec, TimeUnit.SECONDS);
        //判断获取锁成功与否,获取锁成功则返回true，获取锁失败则返回false。
        return Boolean.TRUE.equals(success);
    }
    @Override
    public void unLock() {
        //Collections.singletonList(KEY_PREFIX +  name)将多个key转化为数组
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX +  name), ID_PREFIX + Thread.currentThread().getId());




    }

//    @Override
//    public void unLock() {
//
//        //获取线程中的标识
//        String clientId = ID_PREFIX + Thread.currentThread().getId() + "";
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断是否一致，一致则释放锁
//        if (clientId.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//
//    }
}
