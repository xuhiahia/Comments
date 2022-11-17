package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.format.DateTimeFormatters;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import com.hmdp.utils.RedisConstants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result transCode(String phone, HttpSession session) {
        //校验手机号码
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式不正确");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        log.info("code:{}",code);
        //存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        session.setAttribute("code",code);
        //返回
        return Result.ok();
    }

    @Override
    public Result loginAndRegister(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式不正确");
        }
        String cacheCode = loginForm.getCode();
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
//        String code = (String) session.getAttribute("code");
        //信息正确
        if(cacheCode==null||!code.equals(cacheCode)){
            return Result.fail("验证码有误");
        }
        //查询user用户
        User user = query().eq("phone", phone).one();
        if(user==null){
           user=creatUser(phone);
        }
        //获取随机token用作登入令牌
        String uuid = UUID.randomUUID( true).toString();
        String token=LOGIN_USER_KEY+uuid;
        //将userDTO转为Map存入Hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),CopyOptions.create()
                .setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(token,map);
        //设置有效期
        stringRedisTemplate.expire(token,LOGIN_USER_TTL,TimeUnit.SECONDS);
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前用户Id
        Long userId = UserHolder.getUser().getId();
        //获取现在的日期
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        //拼接key
        String key=USER_SIGN_KEY+userId+time;
        //存入redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result countSign() {
        //获取当前用户Id
        Long userId = UserHolder.getUser().getId();
        //获取现在的日期
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        //拼接key
        String key=USER_SIGN_KEY+userId+time;
        //取出今天之前的签到记录
        List<Long> list = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        //判空
        if(list==null||list.isEmpty()){
            return Result.ok(0);
        }
        Long count = list.get(0);
        if(count==null||count==0){
            return Result.ok(0);
        }
        int num=0;
        //循环遍历
        while(true){
            //最后一位和1做与运算，如果为0就跳出
            if ((num&1)==0) {
                break;
            }else{
                count++;
            }
            num>>>=1;
        }
        return Result.ok(count);
    }

    public User creatUser(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(4));
        save(user);
        return user;
    }
}
