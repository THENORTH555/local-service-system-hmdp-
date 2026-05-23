package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.val;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private IBlogService blogService;
    @Autowired
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Override
    public Result queryBlogById(Long id) {
        //1,根据id查blog
        Blog blog = blogService.getById(id);
        if (blog == null){
            return Result.fail("笔记不存在");
        }
        //2,查询blog有关的用户
        queryblogUser(blog);
        //3,查询blog是否被点赞，设置blog的islike属性值
        isblogliked(blog);
        return Result.ok(blog);
    }

    private void isblogliked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            // 用户未登录，默认未点赞
            blog.setIsLike(false);
            return;
        }
        Long userId = UserHolder.getUser().getId();
        Long id = blog.getId();
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(BooleanUtil.isTrue(score != null));
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        //加 this 的作用
        //明确方法调用来源
        //this.queryblogUser(blog) 明确表示调用当前类的 queryblogUser 方法
        //this.isblogliked(blog) 明确表示调用当前类的 isblogliked 方法
        records.forEach(blog -> {
            this.queryblogUser(blog);
            this.isblogliked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //点赞功能，利用redis的set集合判断用户是否点赞过，没点赞过则点赞数+1，已点赞则取消点赞，点赞数减少1

        //获取当前用户
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO== null){
            return Result.fail("用户未登录");
        }
        Long userId = userDTO.getId();
        String key = "blog:liked:" + id;
        //score(key,value)是查询score值的方法，返回值是double类型
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null){
            //如果已点赞，取消点赞
            //取消点赞
            //数据库点赞数减1
            boolean issuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //删除用户
            if (issuccess) {
                //时间戳是作为分数存在集合的成员中的，这里只需要移除成员分数自然就会消失
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }else {
            //如果未点赞，可以点赞
            //数据库点赞数加1
          boolean issuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //保存用户
            if (issuccess) {//System.currentTimeMillis()是加入的时间戳
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();

    }

    @Override
    public Result queryBloglikes(Long id) {
        //查询top5的点赞用户
        String key = "blog:liked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //做非空判断避免空指针
        if (top5 == null || top5.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据用户id查询用户，返回泛型为userdto的list结合
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService
//.listByIds(ids),这个userservice给的listbyid不能用，因为它根据Id查出来的用户是无序的，所以要用query方法
                //以下为将user类型的List转为userdto的list集合的业务逻辑
                //先带入流
                .query()
                .in("id", ids).last("ORDER BY FIELD(id,"+ idStr +")").list()
                .stream()
                //然后用stream流的map方法将user批量转为userdto
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                //最后用stream流的collect方法将userdto批量转为list集合
                .collect(Collectors.toList());


        return Result.ok(userDTOS);

    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        Long userId = userDTO.getId();
        blog.setUserId(userId);
        // 保存探店博文

        if(blog.getTitle() == null){
            return Result.fail("请填写标题");
        }
       //做非空判断避免空指针
        boolean ifsuccess =  blogService.save(blog);
        //把这个博客的id发送给他的博主的粉丝、

//        //查询笔记作者的所有粉丝，select * from tb_follow where follow_user_id = user_id
//        List<Follow> follows = followService.query()
//                .eq("follow_user_id", userId).list();
//        //推送笔记id给所有粉丝
//        for (Follow follow : follows) {
//            Long followUserId = follow.getUserId();
//            String key = "feeds:" + followUserId;
//            //推送笔记id给粉丝
//            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
//        }
        rabbitTemplate.convertAndSend("blog.finout", "blog", blog.getId());

        // 返回id
        return Result.ok(blog.getId());

    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //先获取当前 用户
        Long userId = UserHolder.getUser().getId();
        //查询收件箱
        String key = "feeds:" + userId;
        //用reverseRangeByScoreWithScores方法倒序查询确保时间戳大即最新的数据先被查出来
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //防空指针非空判断
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }



        //解析数据:blogId,minTime,offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
       for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples){
           ids.add(Long.valueOf(typedTuple.getValue()));
           //不需要可以去获取最小的时间戳了，每次遍历用新的时间戳的值赋给minTime，等走完最后一次循环就可以获取到最小的时间戳
           long Time = typedTuple.getScore().longValue();
           //offset增加的逻辑
          if (Time == minTime){
              os++;
          }else {
              minTime = Time;
              os = 1;
          }
          }
        //根据id查询bolg封装并返回blog集合
        String idStr = StrUtil.join(",", ids);
       //上面那行代码为每个id中间拼接了一个逗号，以确保满足下面SQL方法ORDER BY FIELD的要求
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id,"+ idStr+")").list();
        //进行关联用户和点赞的查询
        for (Blog blog : blogs){
            //2,查询blog有关的用户
            queryblogUser(blog);
            //3,查询blog是否被点赞，设置blog的islike属性值
            isblogliked(blog);
        }
//        封装结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void queryblogUser(Blog  blog) {

            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());

    }
}
