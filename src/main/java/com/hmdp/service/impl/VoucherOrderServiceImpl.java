package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorkerId;
import com.hmdp.utils.SimpleLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    RedissonClient redissonClient;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    RedisWorkerId redisWorkerId;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    @Transactional
    public Result secKillVoucher(Long id) {
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), id.toString(), userId.toString());
        int i = execute.intValue();
        if(i!=0){   //没有资格购买
            String message= i==1?"库存不足":"不可以重复购买";
            return Result.fail(message);
        }
        //有资格购买
        long orderId = redisWorkerId.nextId("order");
        //用消息队列实现异步秒杀 但我没写
        return Result.ok(orderId);
    }

    /* @Override
    @Transactional
    public Result secKillVoucher(Long id) {
        //拿到订单信息
        SeckillVoucher voucher = iSeckillVoucherService.getById(id);
        //查看是否过期
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("活动还没开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("活动已经结束");
        }
        //看看还有没有库存
        Integer stock = voucher.getStock();
        if (stock < 1) {
            return Result.fail("秒杀失败，库存不足");
        }
        Long userId = UserHolder.getUser().getId();
//        SimpleLock lock = new SimpleLock("order:"+userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if(!isLock){
            return Result.fail("同一个用户只能下一单");
        }
//        synchronized (userId.toString().intern()){
        try {
            IVoucherOrderService prox = (IVoucherOrderService) AopContext.currentProxy();
            return prox.createVoucher(id,userId);
        } finally {
            lock.unlock();
        }
//        }
    }*/ //这是用分布式锁实现一人一单

    @Transactional
    public Result createVoucher(Long id,Long userId) {

        //判断用户有没有购买过
        Integer count = query().eq("user_id", userId).count();
        if(count>0){
            return Result.fail("用户已经下单过");
        }
        //还有库存就减少
        boolean success = iSeckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", id).gt("stock",0)
                .update();
        if(!success){
            return Result.fail("库存不足");
        }
        //生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(id);
        long order = redisWorkerId.nextId("order");
        voucherOrder.setId(order);
//         userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        return Result.ok(order);
    }
}
