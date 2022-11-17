package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;

import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    CacheClient cacheClient;
    @Override
    public Result queryShop(Long id) {
        //缓存穿透
//        Shop shop = queryShopWithPassThrough(id);
//        Shop shop1 = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //缓存击穿
//        Shop shop = queryShopWithMetux(id);
        Shop shop = cacheClient.queryWithLogical(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    public Shop queryShopWithMetux(Long id) {//互斥锁解决缓存击穿
        String key=CACHE_SHOP_KEY+id;
        //从Redis中查询缓存
        String cacheShop = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(cacheShop)){
            return JSONUtil.toBean(cacheShop, Shop.class);
        }
        if("".equals(cacheShop)){//如果是空值就直接报错
            return null;
        }
        String lockKey=LOCK_SHOP_KEY+id;
        Shop shop=null;
        try {
            //拿锁
        boolean flag = tryLock(lockKey);
        //没拿到锁，休眠一段时间
        if(!flag){
            Thread.sleep(50);
            queryShopWithMetux(id);
        }
        //拿到锁再检查一下缓存
         String newJson= stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(cacheShop)){
                return JSONUtil.toBean(newJson, Shop.class);
            }
        //拿到锁重构缓存,走数据库
        shop = this.getById(id);
        //数据库查不到报错
        if(shop==null){
            //如果不存在就向redis中缓存null
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //数据库查得到添加到缓存
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }
        return shop;
    }
    //注释的是逻辑过期解决缓存击穿
    /**
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public Shop queryShopWithLogical(Long id) {//逻辑过期解决缓存击穿
        String key=CACHE_SHOP_KEY+id;
        //从Redis中查询缓存
        String CacheredisData = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(CacheredisData)){//redis中查不到就返回
            return null;
        }
        //查得到看看逻辑时间是否过期，如果没有过期就返回，有过期就重构
        RedisData redisData = JSONUtil.toBean(CacheredisData, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){//没过期
            return shop;
        }
        //过期拿锁
        String LockKey=LOCK_SHOP_KEY+id;
        if(tryLock(LockKey)){//拿到锁
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    saveShop2Redis(id,20l);//重构缓存
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(LockKey);
                }
            });

        }
        return shop;
    }
     */
    public void saveShop2Redis(Long id,Long expireTime){//用于数据预热
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
   /**
  public Shop queryShopWithPassThrough(Long id) {//解决缓存穿透
        String key=CACHE_SHOP_KEY+id;
        //从Redis中查询缓存
        String cacheShop = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(cacheShop)){
            return JSONUtil.toBean(cacheShop, Shop.class);
        }
        if("".equals(cacheShop)){//如果是空值就直接报错
            return null;
        }
        //查不到，走数据库
        Shop shop = this.getById(id);
        //数据库查不到报错
        if(shop==null){
            //如果不存在就向redis中缓存null
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //数据库查得到添加到缓存
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    } //这里注释掉的是解决缓存穿透

    */



    private boolean tryLock(String lockKey){//上锁
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unlock(String lockKey){//释放锁
        stringRedisTemplate.delete(lockKey);
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok(shop);
    }
}
