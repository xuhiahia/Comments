package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryById(Long blogId);

    Result queryHot(Integer current);

    Result likeBlog(Long id);

    Result LikePeople(Long blogId);

    Result saveBlog(Blog blog);

    Result queryFocus(Long minTime, Long offset);
}
