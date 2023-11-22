package com.hmdp.service.impl;

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

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.是否存在
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop= JSONUtil.toBean(shopJson, Shop.class);
            //3.存在，返回
            return Result.ok(shop);
        }
        //4.不存在，sql查询
        Shop shop=getById(id);
        if(shop==null){
            //5.不存在，404
            return Result.fail("店铺不存在");
        }
        //6.写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
