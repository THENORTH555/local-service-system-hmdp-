package com.hmdp;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.geo.Point;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopservice;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private IVoucherService voucherService;

    @Test
    void testsaveshop() {
        shopservice.saveShop2Redis(1L, 10L);

    }
    @Test
    void loadShopdata() {
        //1，查询店铺信息
        List<Shop> list = shopservice.list();
        //2,根据店铺类型id分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3，写入redis，Map.Entry 是 Map 集合的内部接口，专门用来表示「一组键值对」
        //<Long, List<Shop>> 表示：这组键值对中，键的类型是 Long，值的类型是 List<Shop>（Shop 实体类的列表）
        //第一个for循环用来拿到map中每个组的id及其组下的shop列表
       for (Map.Entry<Long, List<Shop>> entry : map.entrySet()){
           //获取类型id
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());


            //第二个for循环操作geoAdd方法，将shop列表中的shop对象添加到redis中
            for (Shop shop : value){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
           //这样子装好shopid和坐标的location对象就做好了
           stringRedisTemplate.opsForGeo().add(key, locations);
       }





    }


    @Test
    void testHyperlog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++){
            j = i%1000;
            values[j] = "user:" + i;
            if (j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("h12", values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("h12");
        System.out.println("count一共是 = " + count);

    }
    @Test
    void testseckill() {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(1086L);
        userDTO.setNickName("张三");
        userDTO.setIcon("");
        UserHolder.saveUser(userDTO);

       Result result = voucherOrderService.seckillVoucher(16L);

        System.out.println("秒杀结果"+result);
        if (result.getSuccess()){
            System.out.println("订单id"+result.getData());
        }else {
            System.out.println("错误信息"+result.getErrorMsg());
        }
    }

    @Test
    void setvoucher() {
        try {
            Voucher voucher = new Voucher();
            // 不设置ID，让数据库自增
            voucher.setTitle("圣罗帝使得11亚撒大大11222优惠券");
            voucher.setPayValue(1000L);
            voucher.setActualValue(500L);
            voucher.setStatus(1);
            voucher.setStock(1000);
            voucher.setBeginTime(null);
            voucher.setEndTime(null);
            voucher.setType(1);
            voucher.setShopId(1L);
            voucher.setRules("工作日日落后可使用");

            System.out.println("开始添加秒杀券...");
            voucherService.addSeckillVoucher(voucher);

            // 这里的getId()会返回数据库生成的自增ID
            System.out.println("成功添加秒杀券，实际ID: " + voucher.getId());

            // 使用实际的ID来验证Redis
            String stockKey = "seckill:stock:" + voucher.getId();
            String stock = stringRedisTemplate.opsForValue().get(stockKey);
            System.out.println("Redis中的库存key: " + stockKey);
            System.out.println("Redis中的库存值: " + stock);

        } catch (Exception e) {
            System.err.println("添加秒杀券失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
