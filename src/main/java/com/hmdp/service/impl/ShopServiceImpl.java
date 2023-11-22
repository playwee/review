package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
        Shop shop = queryWithMutex(id);
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
            shop = getById(id);
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
