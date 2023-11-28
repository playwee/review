package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import lombok.val;
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
 * @author wee
 * @since 2023-11-11
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥：缓存击穿
//        Shop shop = queryWithMutex(id);


        //逻辑过期：不需要考虑缓存穿透问题
        Shop shop = queryWithLogicalExpire(id);
        //此方法返回值有可能为null，所以需要做处理
        if(shop==null){
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.是否存在,这里是字符串才是true，空字符串 null都是false
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //穿透结果：命中的是否是一个空字符串(即空值处理过后)，空值处理后可到达
        if(shopJson!=null){
            return null;
        }
        //4.不存在，sql查询
        String lockKey = LOCK_SHOP_KEY + id;
        //4.1实现缓存重建，获取互斥锁，
        Shop shop= null;
        try {
            boolean isLock=tryLock(lockKey);//锁的key需要重新配
            // 4.2 判断是否获取成功
            if(!isLock){
                //休眠短
                Thread.sleep(200);
                queryWithMutex(id);
            }
            // 4.3 失败，休眠且重试
            // 5 成功，根据id查询数据库
            shop = getById(id);//查询数据库
            //模拟重建延迟
//            Thread.sleep(200);
            //未命中，这里开始变化
            if(shop==null){
                //数据库空值：穿透解决
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //5.不存在，404
                return null;
            }
            //6.写入redis,原子性，设置key和设置超时要同时
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }
    public void saveShop2Redis(Long id,Long expireTime) throws InterruptedException {
        //1.查询店铺数据
        Shop shop=getById(id);
        //模拟延迟
//        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //3.写入Redis,测试是否能够加入
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 十个线程池，其他默认
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    /**
     * 逻辑过期：问题若开始就为空，这就g了
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.是否存在,这里是字符串才是true，空字符串 null都是false
        if(StrUtil.isBlank(shopJson)){
            //3.不存在，返回null
            return null;
        }
        //4.命中需要，看是否过期，此时需要反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop=JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime=redisData.getExpireTime();
        //5 是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期
            return shop;
        }
        //5.2 过期，重建，过期外部控制
        //6 获取锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);

        //6.1 判断是否获取锁
        //成功，独立线程，缓存重建
        if(isLock){
            //开始独立线程，进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,LOCK_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.2 返回过期的或者更新完的商铺信息
        return shop;
    }


    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.是否存在,这里是字符串才是true，空字符串 null都是false
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //命中的是否是一个空字符串(即空值处理过后)，空值处理后可到达
        if(shopJson!=null){
            return null;
        }
        //4.不存在，sql查询
        Shop shop=getById(id);
        if(shop==null){
            //数据库空值：穿透解决
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //5.不存在，404
            return null;
        }
        //6.写入redis,原子性，设置key和设置超时要同时
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 获取锁，根据key
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //由于我接收的是一个Boolean，直接返回会做拆箱，拆箱可能出现空指针
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        String key = CACHE_SHOP_KEY + id;
        //1.更新数据库
        updateById(shop);
        //2.更新缓存
        stringRedisTemplate.delete(key);

        return Result.ok();
    }
}
