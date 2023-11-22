package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新配置，order=0
 * 浏览器访问中http头的authorization字段
 * 然后才是redis中获取User，看是否存在，不存在返回true给下一个拦截器判断
 * 存在则将DTO保存在ThreadLocal中，并更新这个DTO的token有效期
 * 对每次访问进行token刷新，用户存在，ThreadLocal能够获取
 * PS：走这个过程的都是需要登录的过程，即redis有存储以及auth有字段
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //redis
        //1 获取token
        String token = request.getHeader("authorization");
        if(StringUtils.isBlank(token)){
            return true;//为空直接,结束,让下一个拦截器取拦截
        }
        //2 获取redis中token对应的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3. 判断用户是否存在
        if(userMap.isEmpty()){
            return true;//为空直接,结束,让下一个拦截器取拦截
        }

        //能获取到token以及其用户
        //4 redis找到的hashMap数据转化成userDTO，fillBeanWithMap
        UserDTO userDTO=BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);

        //5. 存在 用户信息放在ThreadLocal
        //UserHolder 存用户信息的，静态方法
        UserHolder.saveUser(userDTO);

        //6. 刷新token有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        //请求完成后移除这个UserDTO
        UserHolder.removeUser();
    }
}
