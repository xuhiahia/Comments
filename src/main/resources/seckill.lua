--秒杀卷id
local voucherId=ARGV[1]
--用户ID
local userId=ARGV[2]

local stockKey='seckill:stock:'..voucherId
local stockOrder='seckill:order:' .. voucherId

--判断优惠券的库存是否充足 get stock
if(tonumber(redis.call('get',stockKey) <= 0)) then
    return 1;--库存不足
end

--判断用户是否购买资格 sisnumber
if (redis.call('sismember',stockOrder , userId) == 1) then
    return 2;--用户已购买
end

--下单扣库存incr
redis.call('incrby',stockKey,-1)
--保存用户
redis.call('sadd',stockOrder,userId)
return 0;

