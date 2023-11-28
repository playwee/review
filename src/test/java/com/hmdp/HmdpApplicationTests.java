package com.hmdp;

/**
 * 测试类
 */

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
public class HmdpApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 线程池
     */
     private ExecutorService es= Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);
        long begin=System.currentTimeMillis();
        for(int i=0;i<300;++i){
            //300个线程
            es.submit(()->{
                for(int j=0;j<100;++j){
                    long id=redisIdWorker.nextId("order");
                    System.out.println("id="+id);
                }
                latch.countDown();
            });
        }
        latch.await();
        long end=System.currentTimeMillis();
        /**
         * 看具体花费的时间，异步所以latch
         */
        System.out.println("time="+(end-begin));
    }

    @Test
    void testSaveShop() throws InterruptedException{
        Shop shop=shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
    }

}
