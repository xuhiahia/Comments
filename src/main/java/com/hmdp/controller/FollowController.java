package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    IFollowService followService;

    @PutMapping ("/{followId}/{isFollowed}")
    public Result ChooseFollowed(@PathVariable("followId")Long follwoId,@PathVariable("isFollowed")Boolean isFollow){
        return followService.chooseFollow(follwoId,isFollow);
    }

    @GetMapping("/or/not/{followId}")
    public Result queryFollow(@PathVariable("followId")Long follwoId){
        return followService.queryFollow(follwoId);
    }

    @GetMapping("/common/{followId}")
    public Result followCommon(@PathVariable("followId") long id){
      return  followService.queryCommon(id);
    }
}
