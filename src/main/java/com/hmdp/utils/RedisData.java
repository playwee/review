package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑过期要的时间
 * 方案1：继承，但还是需要修改源代码
 * 方案2：添加一个Object属性，data就是想要存在Redis数据，万能数据
 * 后台管理系统可以把热点实际提交过去（预热）
 */
@Data
public class RedisData {

    private LocalDateTime expireTime;
    private Object data;
}
