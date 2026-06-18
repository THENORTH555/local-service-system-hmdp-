package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.rabbitmq.client.Channel;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

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
    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);
    @Resource
 private RedissonClient redissonClient;
 @Resource
    private IVoucherOrderService voucherOrderService;

 @RabbitListener(queues = "seckill.queue2")
    public void listenseckillOrder(VoucherOrder voucherOrder, Channel channel, @Header long deliveryTag){
    Long userId = voucherOrder.getUserId();
    //用redisson中的Rlock接口创建一把锁实例
     RLock lock = redissonClient.getLock("order:" + userId);
//尝试抢锁，没抢到锁就说明这是该用户的多开抢优惠卷的请求，直接返回并拒绝掉
     if (!lock.tryLock()) {
         try {
             channel.basicNack(deliveryTag, false, true);
         }catch (Exception e){
             log.warn("拒绝消息失败");
         }
         return;
     }
    //用try-finally包裹保证锁一定释放
     try {
         //抢到锁之后创建订单
         voucherOrderService.createOrder(voucherOrder);
         channel.basicAck(deliveryTag, false);

     } catch (Exception e) {
         log.warn("订单创建失败，消息将重试",e);
         try {
             channel.basicNack(deliveryTag, false, true);
         }catch (Exception e1){
             log.warn("拒绝消息失败",e1);
         }
     } finally {
         //释放锁，释放锁之后如果该用户再次调用抢优惠卷的接口，走到上面的try的createorder方法中就会因为其中的一人一单判断条件，直接返回并拒绝掉该用户
         lock.unlock();
     }
 }
    }


