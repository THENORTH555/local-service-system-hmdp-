package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j; // 新增：引入日志注解
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j // 新增：开启日志功能
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private final StringRedisTemplate stringRedisTemplate;
    @Resource
    private final RedisTemplate redisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate, @Qualifier("redisTemplate") RedisTemplate redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        boolean isPhoneInvalid = RegexUtils.isPhoneInvalid(phone);
        //不符合则返回错误信息
        if (isPhoneInvalid) {
            log.error("手机号格式错误：{}", phone); // 新增：日志打印
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis，key为手机号，value为验证码,过期时间设置为两分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 新增：打印验证码日志（方便调试，生产环境可删除）
        log.debug("发送验证码成功，手机号：{}，验证码：{}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 新增：打印登录请求参数（方便调试）
        log.info("收到登录请求，手机号：{}，验证码：{}", loginForm.getPhone(), loginForm.getCode());

        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            log.error("登录失败：手机号格式错误，{}", phone); // 新增：日志打印
            return Result.fail("手机号格式错误");
        }

        //校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 新增：打印缓存中的验证码（关键！排查验证码不一致问题）
        log.info("Redis中缓存的验证码：{}，用户输入的验证码：{}", cacheCode, loginForm.getCode());

        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            log.error("登录失败：验证码错误，手机号：{}", phone); // 新增：日志打印
            return Result.fail("验证码错误");
        }

        //查询用户（这里直接用iservice提供的直接查询数据库的query方法）.one()方法查一个，没有就 null，多了就报错
        User user = query().eq("phone", phone).one();
        log.info("查询到用户：{}", user == null ? "不存在" : user.getId()); // 新增：日志打印

        //若存在则登入，若不存在则创建用户
        if (user == null) {
            user = createUserwithPhone(phone);
            log.info("创建新用户：{}", user.getId()); // 新增：日志打印
        }

        //随机生成 token
        String token = UUID.randomUUID().toString();
        log.info("生成了token：{}", token); // 替换System.out为日志，更规范
        System.out.println("生成了token：" + token); // 保留原有打印，确保你能看到

        //保存用户信息到session（可选，保留不影响）
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //把用户信息保存到redis中
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
//这一段是把userDTO转为map，其中每一个属性以及其对应的值都用setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()))转成string-string类型的键值对，这里token是redis中的key，value是userdto转成的哈希表，而哈希表中userdto的属性是key，属性对应的值是value，相当于嵌套键值对
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //token 作为 key，将 userDTO以hash表的形式 存到 redis 中
        String tokenkey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenkey, usermap);
        //设置token有效期
        stringRedisTemplate.expire(tokenkey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        log.info("用户{}登录成功，Token已存入Redis，有效期{}分钟", user.getId(), LOGIN_USER_TTL); // 新增：日志打印
        return Result.ok(token); // 关键：确保这行执行到，返回Token
    }

    @Override
    public Result sign() {
        //获取登录用户
        Long userID = UserHolder.getUser().getId();
        //获取当前日期，即当前天是这个月的第几天
        LocalDateTime now = LocalDateTime.now();
        int dayofMonth = now.getDayOfMonth();
        //拼接key
        String Keysuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userID + Keysuffix;
        //写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayofMonth - 1, true);
        return Result.ok();

    }

    @Override
    public Result signCount() {
        //获取登录用户
        Long userID = UserHolder.getUser().getId();
        //获取当前日期，即当前天是这个月的第几天
        LocalDateTime now = LocalDateTime.now();
        int dayofMonth = now.getDayOfMonth();
        //拼接key
        String Keysuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userID + Keysuffix;

        //----------------以上都是为了拼接key的操作，因为下面要用到key
        //获取这个月的签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayofMonth)).valueAt(0));
        //循环遍历
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null ||num == 0){
            return Result.ok(0);
        }
        int count = 0;
        //循环遍历每一个比特位，与1做与运算，判断是否为1，是则计数器加一，否则break
        while (true){
            if ((num & 1) == 1) {
                count++;
                //复合赋值运算符，等同于num = num >>> 1，>>>是无符号右移运算符，将最右边的数移到最左边，并补0
                num >>>= 1;
            }else {
                break;
            }
        }
        //返回count
        return Result.ok(count);
    }

    //创建用户的方法
    private User createUserwithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}