package com.hmdp.mq;

import com.hmdp.utils.RedisConstants;
import com.rabbitmq.client.Channel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;


@Component
public class ShopCacheDeleteConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RedisConstants.SHOP_DELETE_QUEUE)
    public void listen(Long shopId,Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){

        //加入手动重试机制
        try {
            String key = RedisConstants.CACHE_SHOP_KEY + shopId;
            stringRedisTemplate.delete(key);
            System.out.println("✅ MQ消费者，删除店铺缓存 key:" + key);
            channel.basicAck(deliveryTag,false);
        }catch (Exception e){
            e.printStackTrace();
            try{
                channel.basicNack(deliveryTag,false,true);
            }catch (Exception e1){
                e.printStackTrace();
            }


        }

    }
}
