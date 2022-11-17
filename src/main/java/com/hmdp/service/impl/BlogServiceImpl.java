package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Override
    public Result queryById(Long blogId) {
        Blog blog= this.getById(blogId);
        setUser(blog);
        blogIsliked(blog);
        return Result.ok(blog);
    }

    private void blogIsliked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return;
        }
        Long userId =user.getId();

        String likeKey = BLOG_LIKED_KEY + blog.getId();
        //判断用户有没有点过赞
        Double score = stringRedisTemplate.opsForZSet().score(likeKey, userId.toString());
        blog.setIsLike(score!=null);
    }

    @Override
    public Result queryHot(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            setUser(blog);
            blogIsliked(blog);
        });

        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取登入用户
        Long userId = UserHolder.getUser().getId();
        String likeKey = BLOG_LIKED_KEY + id;
        //判断用户有没有点过赞
        Double score = stringRedisTemplate.opsForZSet().score(likeKey, userId.toString());
        if(score!=null){
            //点过赞，取消，把用户信息移除set
            boolean success = update().setSql("liked=liked-1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().remove(likeKey,userId.toString());
            }
        }else {
            //如果没点过赞，点赞，并把用户信息放到set中
            boolean success = update().setSql("liked=liked+1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().add(likeKey,userId.toString(),System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result LikePeople(Long blogId) {
        //构建存储的key
        String key=BLOG_LIKED_KEY+blogId;
        //拿出top5
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //解析出用户ID
        List<Long> top5 = range.stream().map(Long::valueOf).collect(Collectors.toList());
        //拼接sql
        String join = StrUtil.join(",", top5);
        List<UserDTO> collect = userService.query().in("id", top5).last("ORDER BY FIELD(id," + join + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

      /*  List<User> users = getBaseMapper().getUserById(top5);
        List<UserDTO> collect = users.stream().map(user -> {
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            return userDTO;
        }).collect(Collectors.toList());*///基于xml
        return Result.ok(collect);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean success = save(blog);
        if(!success){//添加博客失败
            return Result.fail("新增博客失败");
        }
        //添加成功,先获取所有的粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", blog.getUserId()).list();
        for(Follow follow:follows){
            String key=FEED_KEY+follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryFocus(Long minTime, Long offset) {
        //获取用户Id
        Long UserId = UserHolder.getUser().getId();
        //获取信箱
        String key=FEED_KEY+UserId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, minTime, offset, 2);
        if(typedTuples==null||typedTuples.isEmpty()){//判断信箱是否为空
            return Result.ok();
        }
        long Time=0;
        Integer os=1;//表示偏移量
        ArrayList<Long> arrayList = new ArrayList(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            arrayList.add( Long.valueOf(typedTuple.getValue()));
            long score = typedTuple.getScore().longValue();
            if(score==Time){
                os++;
            }else{
                Time=score;
                os=1;
            }
        }
        List<Blog> blogs = arrayList.stream().map((id) -> {//查询博客并且为他查找点赞数
            Blog blog = getById(id);
            setUser(blog);
            blogIsliked(blog);
            return blog;
        }).collect(Collectors.toList());
        ScrollResult scrollResult = new ScrollResult();//封装返回的数据
        scrollResult.setList(blogs);
        scrollResult.setMinTime(Time);
        scrollResult.setOffset(os);

        return Result.ok(scrollResult);
    }

    private void setUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
