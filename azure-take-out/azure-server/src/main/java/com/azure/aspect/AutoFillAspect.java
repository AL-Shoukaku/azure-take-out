package com.azure.aspect;

import com.azure.annotation.AutoFill;
import com.azure.constant.AutoFillConstant;
import com.azure.context.BaseContext;
import com.azure.entity.Employee;
import com.azure.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面类，实现公共字段的自动填充
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * 切入点，对哪些类的哪些方法进行拦截
     */
    // 拦截 mapper 包下的所有类的所有方法，且有 AutoFill 注解
    @Pointcut("execution(* com.azure.mapper.*.*(..)) && @annotation(com.azure.annotation.AutoFill)")
    public void autoFillPointCut() {}

    /**
     * 前置通知，在调用方法之前进行操作
     * 在拦截器拦截时执行该方法，被拦截方法的AutoFill注解中含有操作类型
     *
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段的自动填充...");

        // 获取被拦截方法的操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 获取被拦截方法的签名信息
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class); // 获取被拦截方法的 AutoFill 注解
        OperationType operationType = autoFill.value();

        // 获取被拦截方法操作的 employee 对象
        Object[] args = joinPoint.getArgs();    // 获取被拦截方法的参数列表
        if (args == null || args.length == 0) {
            log.info("进行公共字段填充时拦截方法无任何参数，方法名：{}", signature.getMethod().getName());
            return; // 没有任何参数，直接返回
        }
        Object entity = args[0];    // 约定：第一个参数是 employee，category 等的实体

        // 根据操作类型为公共字段进行赋值，使用反射获取对应的方法，使用常量代表方法名
        LocalDateTime now = LocalDateTime.now();
        long id = BaseContext.getCurrentId();
        if (operationType == OperationType.INSERT) {
            // 插入操作
            try {
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                setCreateTime.invoke(entity, now);
                setUpdateTime.invoke(entity, now);
                setCreateUser.invoke(entity, id);
                setUpdateUser.invoke(entity, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            // 更新操作
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
