package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenInterceptor.class);
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override // 补充@Override注解，确保重写正确
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // ========== 核心修改：优先从 Session 取用户（适配你的 Session 认证） ==========
//        HttpSession session = request.getSession();
//        UserDTO sessionUser = (UserDTO) session.getAttribute("user");
//        if (sessionUser != null) {
//            // Session 中有用户，直接存入 UserHolder
//            UserHolder.saveUser(sessionUser);
//            System.out.println("RefreshTokenInterceptor 从Session取到用户：" + sessionUser);
//            return true;
//        }

        // ========== Token 认证核心逻辑（仅处理前缀，不依赖Session） ==========
        // 1. 获取请求头中的token（小写authorization，和前端匹配）
        String token = request.getHeader("authorization");
        log.info("从请求头获取的原始Token：{}", token);

        // 2. 处理Bearer前缀：截取纯Token（关键修复）
        if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7); // 去掉"Bearer "前缀（含空格共7个字符）
            log.info("处理后纯Token：{}", token);
        }

        // 3. 判断处理后的token是否为空
        if (StrUtil.isBlank(token)) {
            return true;
        }
        log.info("token不为空，开始查询Redis");

        // 4. 拼接Redis key，查询用户信息
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> usermap = stringRedisTemplate.opsForHash().entries(key);

        // 5. 判断Redis中是否存在该用户
        if (usermap.isEmpty()) {
            log.info("Redis中未找到该Token对应的用户，直接放行");
            return true;
        }

        // 6. 将Map转换为UserDTO，存入ThreadLocal（核心：让LoginInterceptor能拿到用户）
        UserDTO userDTO = BeanUtil.fillBeanWithMap(usermap, new UserDTO(), false);
        log.info("从Redis中取到用户：{}", userDTO);
        UserHolder.saveUser(userDTO);

        // 7. 刷新Token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        log.info("已刷新Token有效期：{}分钟", RedisConstants.LOGIN_USER_TTL);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 1.移除用户，避免内存泄漏
        UserHolder.removeUser();
    }
}