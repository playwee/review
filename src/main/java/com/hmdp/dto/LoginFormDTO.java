package com.hmdp.dto;

import lombok.Data;

/**
 * 登录参数
     * 电话
     * code
     * 密码
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
