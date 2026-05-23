package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ClassName:LoginInterceptor
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/9/22 15:11
 * @Version 1.0
 */

public class LoginInterceptor implements HandlerInterceptor {


//该拦截器仅用于判断用户是否登录，因此不需要处理用户信息
//@Override
//1. HttpServletRequest request
//    作用：代表客户端发来的 HTTP 请求对象，封装了本次请求的所有信息。
//2. HttpServletResponse response
//    作用：代表服务端要返回给客户端的 HTTP 响应对象，用于控制返回给客户端的内容。
//          Object handler
//       代表本次请求最终要执行的目标处理器（通常是 Controller 中的某个方法）。
//spring MVC的三个标准入参
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//    // 1.获取session
//    HttpSession session = request.getSession();
//    // 2.获取用户
//    Object user = session.getAttribute("user");
//    // 添加日志
//    System.out.println("从 Session 中获取到的用户: " + user);
//    System.out.println("Session ID: " + session.getId());
//    if (UserHolder.getUser() == null) {
//        response.setStatus(401);
//        return false;
//    }
//
//    UserHolder.saveUser((UserDTO) user);
//        return true;
//}
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 判断是否登录（看ThreadLocal里有没有用户）
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 未登录，拦截
            response.setStatus(401);
            return false;
        }
        // 已登录，放行
        return true;
    }


@Override
    public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) throws Exception{

UserHolder.removeUser();
}
}
