--优惠券id
local vocherID = ARGV[1]
--用户id
local userID = ARGV[2]
--订单id
local orderID = ARGV[3]

--数据key

--库存key
local stockKey = "seckill:stock:" .. vocherID

--订单key
local orderKey = "seckill:order:" .. vocherID

--脚本业务
--判断库存是否充足get stockKey，tonumber是lua脚本语言中的将数据类型转为number的方法
if (tonumber(redis.call("get", stockKey)) <= 0 )then
    --3.2 库存不足返回1
    return 1
end
--判断用户是否重复抢购，用sismember命令
if (redis.call("sismember", orderKey, userID) == 1) then
    --3.3 用户重复抢购返回2
    return 2
end
--3.1 减库存，用incrby命令
redis.call("incrby", stockKey, -1)
--3.4 下单，用sadd命令
redis.call("sadd", orderKey, userID)
--3.5 发送消息到队列当中
redis.call("xadd", "stream.orders", "*", "userID", userID, "vocherID", vocherID, "orderID", orderID)
return 0