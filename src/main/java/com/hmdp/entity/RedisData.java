package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * ClassName:RedisData
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/10/10 15:49
 * @Version 1.0
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;


}
