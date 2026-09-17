package com.sky.annotation;

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于公共字段的自动填充处理，如更新时间等
 */
@Target(ElementType.METHOD) // 该注解只能用于方法上
@Retention(RetentionPolicy.RUNTIME) // 该注解在运行时仍然保留
public @interface AutoFill {
    // 指定数据库操作类型：Insert， Update
    OperationType value();
}
