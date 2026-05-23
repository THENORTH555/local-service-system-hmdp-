package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class  ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        String shoptypejson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shoptypejson)) {
            //用jsonutil中的tobean方法把json转为shop对象,这里不能用tobean方法来反序列化，因为很有可能接受到的是一个集合，要转化成json数组
            List<ShopType> shoplist = JSONUtil.toList(JSONUtil.parseArray(shoptypejson), ShopType.class);
            return Result.ok(shoplist);
        }
        //redis中缓存未命中，则查询数据库
        //query().orderByAsc("sort").list() 是 MyBatis-Plus 提供的链式查询方法，具体说明如下：
        //query()：这是 ServiceImpl 类提供的方法，用于创建一个查询构造器 QueryWrapper
        //orderByAsc("sort")：是 QueryWrapper 的方法，用于指定按 sort 字段进行升序排序
        //list()：是 QueryWrapper 的方法，用于执行查询并返回结果列表
        //整体作用是：查询所有店铺类型数据，并按照 sort 字段的升序进行排列
        //这是 MyBatis-Plus 的 LambdaQueryChainWrapper 链式调用语法，可以让查询条件的构建更加直观和流畅。
         List<ShopType> shoplist = query().orderByAsc("sort").list();
        if (shoplist == null) {
            return Result.fail("没有查询到数据");
        }
        //将数据写入redis,添加缓存
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shoplist));
        return Result.ok(shoplist);
    }
}
