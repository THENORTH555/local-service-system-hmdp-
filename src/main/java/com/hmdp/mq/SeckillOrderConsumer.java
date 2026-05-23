package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * ClassName:SeckillOrderConsumer
 * Description:
 *
 * @Author 何永琪
 * @Create 2026/5/20 18:57
 * @Version 1.0
 */
@Component
public class SeckillOrderConsumer {
 @Resource
 private RedissonClient redissonClient;
 @Resource
    private IVoucherOrderService voucherOrderService;

 @RabbitListener(queues = "seckill.queue2")
    public void listenseckillOrder(VoucherOrder voucherOrder){
    Long userId = voucherOrder.getUserId();
    //用redisson创建一把锁
     RLock lock = redissonClient.getLock("order:" + userId);
//尝试抢锁，没抢到锁就说明这是该用户的多开抢优惠卷的请求，直接返回并拒绝掉
     if (!lock.tryLock()) {
         return;
     }
    //用try-finally包裹保证锁一定释放
     try {
         //抢到锁之后创建订单
         voucherOrderService.createOrder(voucherOrder);
     } finally {
         //释放锁，释放锁之后如果该用户再次调用抢优惠卷的接口，走到上面的try的createorder方法中就会因为其中的一人一单判断条件，直接返回并拒绝掉该用户
         lock.unlock();
     }
 }
    }


