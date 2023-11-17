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
        //2 获取token的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //3. 判断用户是否存在
        if(userMap.isEmpty()){
            return true;//为空直接,结束,让下一个拦截器取拦截
        }

        //能获取到token以及其用户
        //4 Map数据转化成userDTO
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
        UserHolder.removeUser();
    }
}
