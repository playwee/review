package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取Session
        HttpSession session=request.getSession();
        //2.获取Session中用户
        Object user=session.getAttribute("user");
        //3. 判断用户是否存在

        //4.不存在 拦截
        if(user==null){
            response.setStatus(401);//未授权的意思
            return false;
        }

        //5. 存在 用户信息放在ThreadLocal
        //UserHolder 存用户信息的，静态方法
        UserHolder.saveUser((UserDTO) user);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        return;
    }
}
