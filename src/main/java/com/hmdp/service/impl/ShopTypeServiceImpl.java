package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author wee
 * @since 2023-11-11
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryListByType() {
        String key= RedisConstants.CACHE_SHOP_TYPE_KEY;
        //1.从redis找，看是否存在数据
        Long size=stringRedisTemplate.opsForList().size(key);
        List<ShopType> shopList = new ArrayList<>();
        //2.找到了直接返回
        if(size!=null&&size>0){
            List<String> cacheQueryTypeList = stringRedisTemplate.opsForList().range(key, 0, size - 1);
            if(cacheQueryTypeList!=null){
                shopList=cacheQueryTypeList.stream().map(x-> JSONUtil.toBean(x,ShopType.class)).collect(Collectors.toList());
            }
            return Result.ok(shopList);
        }
        //3.没找到去数据库找
        shopList=query().orderByAsc("sort").list();
        //4.没找到，返回空数据
        if(CollectionUtil.isEmpty(shopList)){
            return Result.ok(shopList);
        }
        //5.找到写进redis中
        //将ShopType流转化成json的str
        List<String> redisShopTypeList=shopList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key,redisShopTypeList);
        stringRedisTemplate.expire(key,1, TimeUnit.HOURS);//1h的ddl
        //6 返回
        return Result.ok(shopList);
    }
}
