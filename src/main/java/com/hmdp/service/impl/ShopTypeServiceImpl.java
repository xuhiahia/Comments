package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CATHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result listType() {
        //从redis中查询
        String JsonType = stringRedisTemplate.opsForValue().get(CATHE_SHOPTYPE_KEY);
        if(StrUtil.isNotBlank(JsonType)){
            List<ShopType> shopTypes = JSONUtil.toList(JsonType, ShopType.class);
            return Result.ok(shopTypes);
        }
        //查不到走数据库
        List<ShopType> sort = query().orderByAsc("sort").list();
        //将数据库中的添加到缓存中
        String toJsonStr = JSONUtil.toJsonStr(sort);
        stringRedisTemplate.opsForValue().set(CATHE_SHOPTYPE_KEY,toJsonStr);
        return Result.ok(sort);
    }
}
