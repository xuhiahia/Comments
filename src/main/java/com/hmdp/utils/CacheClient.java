package com.hmdp.utils;

import cn.hutool.core.lang.func.Func;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogical(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //解决缓存穿透
    public <R,ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID,R> dbFunction
    ,Long time,TimeUnit unit) {//解决缓存穿透
        String key=prefix+id;
        //从Redis中查询缓存
        String cacheObj = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(cacheObj)){
            return JSONUtil.toBean(cacheObj, type);
        }
        if("".equals(cacheObj)){//如果是空值就直接报错
            return null;
        }
        //查不到，走数据库
        R r = dbFunction.apply(id);
        //数据库查不到报错
        if(r==null){
            //如果不存在就向redis中缓存null
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //把数据库中的数据添加到缓存
        this.set(key,r,time,unit);
        return r;
    }

    //解决缓存击穿


    public <R,ID> R queryWithLogical(String prefix, ID id, Class<R> type, Function<ID,R>dbFallBack
    ,Long time,TimeUnit unit) {//逻辑过期解决缓存击穿
        String key=prefix+id;
        //从Redis中查询缓存
        String CacheredisData = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(CacheredisData)){//redis中查不到就返回
            return null;
        }
        //查得到看看逻辑时间是否过期，如果没有过期就返回，有过期就重构
        RedisData redisData = JSONUtil.toBean(CacheredisData, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){//没过期
            return r;
        }
        //过期拿锁
        String LockKey=LOCK_SHOP_KEY+id;
        if(tryLock(LockKey)){//拿到锁
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R  r1  = dbFallBack.apply(id);
                    setWithLogical(key,r1,time,unit);//重构缓存
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(LockKey);
                }
            });

        }
        return r;
    }
    private boolean tryLock(String lockKey){//上锁
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unlock(String lockKey){//释放锁
        stringRedisTemplate.delete(lockKey);
    }

}
