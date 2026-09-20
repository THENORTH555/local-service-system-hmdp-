package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.VoucherServiceImpl;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.Arrays;


@Slf4j
@Component
public class SeckillDlxConsumer {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setScriptText("""
                redis.call('incrby', KEYS[1], 1)
                redis.call('del', KEYS[2])
                return 1
                """);
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @RabbitListener(queues = "seckill.dlx.order.queue")
    public void ListenDlx(VoucherOrder voucherOrder,Channel channel,@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        try {


            long Voucherid = voucherOrder.getVoucherId();
            long userid = voucherOrder.getUserId();
            long voucherorderid = voucherOrder.getId();

            VoucherOrder order = voucherOrderService.getById(voucherorderid);

            if (order == null || order.getStatus() == 2) {
                return;
            }
            //若订单未支付执行回滚
            if (order.getStatus() == 1) {
                stringRedisTemplate.execute(ROLLBACK_SCRIPT,
                        Arrays.asList("seckill:stock:" + Voucherid, "seckill:order:" + Voucherid + ":" + userid));
                // 2. DB库存+1
                voucherService.update()
                        .setSql("stock = stock + 1")
                        .eq("id", Voucherid)
                        .update();
                //订单设置为已取消
                order.setStatus(4);
                voucherOrderService.updateById(order);
            }
            channel.basicAck(deliveryTag,false);
        }catch (Exception e){

            e.printStackTrace();
            log.error("死信消息处理异常，订单id:{}",voucherOrder.getId(),e);
            try {
                channel.basicNack(deliveryTag,false,false);
            }catch (Exception e1){
                e1.printStackTrace();
                log.error("死信消息处理失败，不再进入队列",e1);
            }

        }



    }

}
