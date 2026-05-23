package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
@Resource
    StringRedisTemplate stringRedisTemplate;
@Autowired
    private IUserService userService;
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //判断到底是关注还是取关
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            //传进来的是关注请求，保存数据到数据库中
            Follow follow = new Follow();
            follow.setUserId(UserHolder.getUser().getId());
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //保存成功，更新redis中的数据
                String followsKey = "follows:" + userId;
                stringRedisTemplate.opsForZSet().add(followsKey, followUserId.toString(), System.currentTimeMillis());
            }
        }
        else {
            //传进来的是取关请求,从数据库中删除对应用户id的关注
            boolean issuccess =  remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            //更新redis中的数据
            if (issuccess){
                stringRedisTemplate.opsForZSet().remove("follows:" + userId, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
      Integer count = query().eq("user_id", UserHolder.getUser().getId()).count();
      //不需要加一层if条件判断语句了,直接在count > 0的时候返回true
//      if (count > 0){
//          return Result.ok(true);
//      }else {
//          return Result.ok(false);
//      }
      return Result.ok(count > 0);
    }

    @Override
    public Result common(Long id) {
        //获取当前登录 用户
        Long userId = UserHolder.getUser().getId();
        //求交集
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForZSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()){
            //没有共同关注
            return Result.ok(Collections.emptyList());
        }
        //解析id集合，转成long型
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户信息,这里是查到一堆user对象之后用stream流进行批量转换成userdto对象了
        List<UserDTO> userDTOS = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
