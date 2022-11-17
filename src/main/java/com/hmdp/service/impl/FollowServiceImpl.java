package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.time.LocalDateTime;
import java.util.Collection;
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
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    IUserService userService;
    @Override
    public Result chooseFollow(Long follwoId, Boolean isFollow) {
        //获取用户Id
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
        if (BooleanUtil.isTrue(isFollow)) {
            //要关注
            Follow follow = new Follow();
            follow.setFollowUserId(follwoId);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            boolean success = save(follow);
            if(success){
                stringRedisTemplate.opsForSet().add(key,follwoId.toString());
            }
        }else{
            //取消关注
//            LambdaQueryWrapper<Follow> queryWrapper=new LambdaQueryWrapper<>();
//            queryWrapper.eq(Follow::getFollowUserId,follwoId).eq(Follow::getUserId,userId);
            //            remove(queryWrapper);
            boolean success = remove(new QueryWrapper<Follow>().eq("follow_User_Id", follwoId).
                    eq("user_id", userId));
            if (success) {
                stringRedisTemplate.opsForSet().remove(key,follwoId.toString());
            }

        }
        return Result.ok();
    }

    @Override
    public Result queryFollow(Long follwoId) {
        LambdaQueryWrapper<Follow> queryWrapper=new LambdaQueryWrapper<>();
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", follwoId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result queryCommon(long id) {
        //获取用户Id
        Long userId = UserHolder.getUser().getId();
        String key1="follows:"+userId;
        String key2="follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect==null||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> list = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> collect = userService.listByIds(list).stream().map((item) -> {
            UserDTO userDTO = BeanUtil.copyProperties(item, UserDTO.class);
            return userDTO;
        }).collect(Collectors.toList());
        return Result.ok(collect);
    }
}
