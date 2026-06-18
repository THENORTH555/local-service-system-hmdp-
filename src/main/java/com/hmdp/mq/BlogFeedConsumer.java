package com.hmdp.mq;

import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import com.hmdp.service.impl.BlogServiceImpl;
import com.hmdp.service.impl.FollowServiceImpl;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * ClassName:BlogFeedConsumer
 * Description:
 *
 * @Author 何永琪
 * @Create 2026/5/22 17:01
 * @Version 1.0
 */
@Component
public class BlogFeedConsumer {
    private static final Logger log = LoggerFactory.getLogger(BlogFeedConsumer.class);
    @Resource
    private BlogServiceImpl blogServiceimpl;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = "blog.queue")
    public void listen(Long blogid, Channel channel, @Header long deliveryTag){
        try {


            Long userId = blogServiceimpl.getById(blogid).getUserId();
            // 2. 查询作者的所有粉丝
            List<Follow> follows = followService.query()
                    .eq("follow_user_id", userId).list();

            // 推送到粉丝 feeds
            for (Follow follow : follows) {
                Long fanId = follow.getUserId();
                stringRedisTemplate.opsForZSet().add("feeds:" + fanId, blogid.toString(), System.currentTimeMillis());
            }
            //消息发送成功，进行手动消息确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("消息消费异常,进行消息拒绝尝试", e);
            try {
                channel.basicNack(deliveryTag, false, true);
            }catch (Exception e1){
                log.error("消息拒绝失败", e1);
            }
        }

    }



}
