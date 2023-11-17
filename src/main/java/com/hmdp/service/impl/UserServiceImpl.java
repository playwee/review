package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wee
 * @since 2023-11-11
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.手机号获取，验证手机号的是否存在
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不符！");
        }

        //2. 手机号存在，生成随机数，作为code
        String code = RandomUtil.randomNumbers(6);

        //3.日志打印
        log.debug("发送验证码成功,验证码:{}",code);

        //4.写入session，服务器记录了这个session连接
        session.setAttribute("code",code);
        //5.发送验证码
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //此时发过来的是带手机号+验证码或者手机号+密码的表单
        //1.检验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.注册和登录一起,检验验证码的正确性
        String code=loginForm.getCode();
        Object cachecode=session.getAttribute("code");//找服务器缓存的code，只能单机
        //3.不存在或者不一致报错
        if(cachecode==null||!code.equals(cachecode.toString())){
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号找到相应用户，判断是否存在用户
        //找数据库 select * from tb_user
        User user = query().eq("phone", phone).one();//单条记录
        //5.不存在，创建新用户并保存
        if(user==null){
            user=createUserWithPhone(phone);//根据phone注册
        }
        //6. 保存用户信息到session中
        session.setAttribute("code",code);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user=new User();
        user.setPhone(phone);
        //用到utils中设置的常量,加10长度的随机字符串
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //保存，mbp
        save(user);
        return user;
    }

}
