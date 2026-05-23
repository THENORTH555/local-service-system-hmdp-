package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.SneakyThrows;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static cn.hutool.core.util.RuntimeUtil.getResult;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    //注入优惠卷全局唯一id生成器
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private RedisTemplate redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonclient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    @Resource
    private RabbitTemplate rabbitTemplate;
//    private BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024*1024);
//    //使用Executors.newSingleThreadExecutor()创建一个只有一个工作线程的线程池
//    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

//    @PostConstruct
//    //使init方法在类加载时就执行
//    private void init() {
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderTask());
//    }

    //创建一个线程任务类,在这个线程中执行任务，把阻塞队列中的订单取出来执行handleVoucherOrder方法
//    public class VoucherOrderTask implements Runnable{
//        String queueName = "stream.orders";
//
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    //获取消息队列中的消息
//                    //MapRecord<String, Object, Object> 是 Spring Data Redis 中表示 Redis Stream 消息记录的数据类型
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                          Consumer.from("g1", "c1"),
//                            //创建一个StreamReadOptions对象，设置参数，读写一个消息，阻塞两秒
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
//
//                    );
//
//                    //判断是否获取成功
//                    if (list == null || list.isEmpty()){
//                        //如果获取失败，说明没有消息，等待后重新获取
//                        continue;
//                    }
//
//                    //解析获取到的消息
//                   //MapRecord是Spring Data Redis中表示Stream消息的数据类型
//                    MapRecord<String, Object, Object> record = list.get(0);
//
//                    //创建一个map对象接收消息体
//                    Map<Object, Object> value = record.getValue();
//
//                    //使用Hutool工具类的BeanUtil.fillBeanWithMap()方法
//                    //将record.getValue()（Map类型的消息体）转换并填充到新的VoucherOrder对象中
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
//                    //如果获取成功，说明有消息，下单
//
//                    handleVoucherOrder(voucherOrder);
//                    //获取成功要更新ack
//                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1",record.getId());
//                }catch (Exception e) {
//                    log.error("处理订单异常", e);
//                    handlependingList();
//
//            }
//
//        }
//    }
//
//        private void handlependingList() {
//            while (true){
//                try {
//                    //获取pendinglist中消息
//                    //MapRecord<String, Object, Object> 是 Spring Data Redis 中表示 Redis Stream 消息记录的数据类型
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            //创建一个StreamReadOptions对象，设置参数，读写一个消息，阻塞两秒
//                            StreamReadOptions.empty().count(1),
//                            StreamOffset.create(queueName, ReadOffset.from("0"))
//
//                    );
//
//                    //判断是否获取成功
//                    if (list == null || list.isEmpty()){
//                        //如果获取失败，说明pendinglist没有消息
//                       break;
//                    }
//                    //解析获取到的消息
//                    //使用Hutool工具类的BeanUtil.fillBeanWithMap()方法
//
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> value = record.getValue();
//                    //将record.getValue()（Map类型的消息体）转换并填充到新的VoucherOrder对象中
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
//                    //如果获取成功，说明有消息，下单
//
//                    handleVoucherOrder(voucherOrder);
//                    //获取成功要更新ack
//                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1",record.getId());
//
//
//                }catch (Exception e) {
//                    log.error("处理订单异常", e);
//
//                }
//
//            }
//        }
//        }

//    //创建一个线程任务类,在这个线程中执行任务，把阻塞队列中的订单取出来执行handleVoucherOrder方法
//    public class VoucherOrderTask implements Runnable{
//        @SneakyThrows
//        @Override
//        public void run() {
//        while (true){
//            try {
//                VoucherOrder voucherOrder = queue.take();
//                handleVoucherOrder(voucherOrder);
//            }catch (Exception e) {
//                log.error("处理订单异常", e);
//            }
//
//        }
//
//        }
//    }

    //无返回值方法，作用仅为获取锁并判断请求是否合法，合法则调用createOrder方法创建订单
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userid = voucherOrder.getUserId();
        RLock simpleRedisLock = redissonclient.getLock("order:" + userid);


        //获取锁
        Boolean islock = simpleRedisLock.tryLock();
        //判断是否获取锁成功
        if (!islock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            // 延迟获取代理对象
            if (proxy == null) {
                proxy = (IVoucherOrderService) AopContext.currentProxy();
            }
            proxy.createOrder(voucherOrder);
        } finally {
            //释放锁
            simpleRedisLock.unlock();
        }
    }

    //定义一个脚本类的实体并且用静态代码块为其初始化
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //设置其对应的脚本文件路径
        //ClassPathResource：明确来自 Spring 框架（org.springframework.core.io.ClassPathResource），是 Spring 核心 IO 模块中用于访问类路径资源的工具类。
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        //设置返回值类型为Long
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private IVoucherOrderService proxy;

    //用户参与秒杀活动进入该秒杀方法
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取订单id
        Long orderId = redisWorker.nextId("order");
        //从该请求的线程中获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本, stringRedisTemplate.execute()是RedisTemplate中的一个方法，用于执行 Redis 脚本。由于excute()方法返回的数据类型固定为object，所以要把他转成long型
        //Lua 保证：库存查询、用户下单判断、预扣库存标记三者整体原子执行
        Long result = (Long) stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.EMPTY_LIST,
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId));
        //判断结果是否为0，否则没有购买资格，这里的结果是由Lua脚本执行完返回过来的
        int r = result.intValue();
        if (r != 0){
            switch (r) {
                case 1:
                    return Result.fail("库存不足");
                case 2:
                    return Result.fail("不能重复下单");
            }
        }
        //发送订单信息到队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);

        rabbitTemplate.convertAndSend("seckill.direct", "seckill.order", voucherOrder);
        return Result.ok(orderId);
    }
//    public Result seckillVoucher(Long voucherId) {
//
//        //获取用户id
//        Long userId = UserHolder.getUser().getId();
//        //执行lua脚本
//        Long result = (Long) stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.EMPTY_LIST, voucherId.toString(), userId.toString());
//        //判断结果是否为0，否则没有购买资格
//        int r = result.intValue();
//        if (r != 0){
//            switch (r) {
//                case 1:
//                    return Result.fail("库存不足");
//                case 2:
//                    return Result.fail("不能重复下单");
//            }
//        }
//        //若为0则则有购买资格，把下单信息保存到阻塞队列中，等待处理
//        //先把vocherder对象new出来并且赋值
//        VoucherOrder voucherOrder = new VoucherOrder();
//        Long orderId = redisWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//     TODO 保存到阻塞队列
//        queue.add(voucherOrder);
//        在主线程中拿代理对象给子线程的方法调用

        //IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//
//
//        //返回订单id
//    return Result.ok(orderId);
//    }

//        //查询优惠卷
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //判断是否过期，如果过期则返回错误信息
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("优惠券尚未开始");
//        }
//        //判断秒杀是否结束
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("优惠券已结束");
//        }
//        //判断优惠卷库存是否充足，如果不充足则返回错误信息
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("优惠券已售罄");
//        }
//        Long userid = UserHolder.getUser().getId();
//        //intern() 方法会将字符串放入常量池：如果常量池中已存在该字符串（如 "10086"），则直接返回常量池中的引用；如果不存在，则将当前字符串存入常量池后返回引用。
//        //这确保了 相同 userid 对应的锁对象是同一个（常量池中的唯一实例）
//
//        //这里使用reids简单分布式锁包裹了事务
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userid);
//        RLock simpleRedisLock = redissonclient.getLock("order:" + userid);
//
//
//        //获取锁
//        Boolean islock = simpleRedisLock.tryLock();
//        //判断是否获取锁成功
//        if (!islock) {
//            return Result.fail("请勿重复下单");
//        }
//        try {
//            //拿代理对象以防事务失效
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createOrder(voucherId, seckillVoucher);
//        } finally {
//            //释放锁
//            simpleRedisLock.unlock();
//        }

    //因为涉及到了两张表的更改，为了防止线程安全问题，使用事务\

    @Transactional
    //调用创建订单方法，保存订单信息到数据库，一路上都有对库存和重复下单的判断，有点冗余，实际业务逻辑中可以删除
    public void createOrder(VoucherOrder voucherOrder) {
        //做一人一单的判断
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        boolean isonly = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count() < 1;
        if (!isonly) {
            log.error("请勿重复下单");

        }


        //扣减库存，并对voucher进行更新操作
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        seckillVoucher.setStock(seckillVoucher.getStock() - 1);
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                //只要查到的库存大于0那么就同步进行更新
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }


        save(voucherOrder);

    }


}
