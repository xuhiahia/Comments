package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    public void test(){
        shopService.saveShop2Redis(1l,30l);
    }

}
