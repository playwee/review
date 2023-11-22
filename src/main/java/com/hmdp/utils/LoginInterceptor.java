package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 拦截：包括找不到redis，不存在auth，此时都可以发现UserHolder.getUser()==null
 * 均返回401
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断是否需要拦截(手机号下的ThreadLocal是否有用户)
        if(UserHolder.getUser()==null) {
            //auth存在且redis找到对应的token的值才能去完成这个操作
            response.setStatus(401);
            return false;
        }
        //放行,有用户
        return true;
    }
}
