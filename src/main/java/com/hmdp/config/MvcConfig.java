package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * ClassName:MvcConfig
 * Description:
 *
 * @Author 何永琪
 * @Create 2025/9/22 15:27
 * @Version 1.0
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    //这里的主要作用是自动查找并装配（注入）应用程序所需的依赖对象，无需手动通过 new 关键字创建实例
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry是INterceptorRegistry的实例，它的作用是拦截.addPathPatterns()方法下路径的请求并且放行excludePathPatterns()方法下的路径
//        registry.addInterceptor(new LoginInterceptor()).order(1)//注册自定义拦截器,order中值越大，该拦截器的优先级越低
//               .addPathPatterns("/**")//规定拦截路径
//              .excludePathPatterns(//规定放行路径
//                        "/user/code",
//                        "/user/login",
//                        "/blog/hot",
//                        "/shop/**",
//                        "/shop-type/**",
//                        "/upload/**",
//                        "/voucher/**"
//
//
//                );
//        能保证刷新token的逻辑
//        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0)
//                .addPathPatterns("/**")
//        .excludePathPatterns(
//                "/user/code",
//                "/user/login",
//                "/blog/hot",
//                "/shop/**",
//                "/shop-type/**",
//                "/upload/**",
//                "/voucher/**",
//                "/doc.html",
//                "/webjars/**",
//                "/v3/api-docs/**",
//                "/swagger-resources",
//                "/swagger-resources/**",
//                "/favicon.ico"
//        );

        // 1. 刷新token拦截器：所有请求，自动续期
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .order(0)
                .addPathPatterns("/**");
        // 2. 登录拦截器：拦截需要登录的接口
        registry.addInterceptor(new LoginInterceptor())
                .order(1)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**"
                );
    }
}
