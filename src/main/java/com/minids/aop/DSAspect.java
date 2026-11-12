package com.minids.aop;

import com.minids.annotation.DS;
import com.minids.context.DataSourceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 数据源切换切面：拦截标了 @DS 的方法，执行前把数据源 key 塞进 ThreadLocal，执行后清理。
 * <p>
 * 它在整条链路里的位置：
 *   用户标 @DS("slave") → 本切面 set("slave") → 业务方法里的 getConnection()
 *   → RoutingDataSource.determineCurrentLookupKey() 读到 "slave" → 派到 slave 库
 *   → 方法结束本切面 clear()
 * <p>
 * @Aspect 只是个标记，类本身必须注册成 Bean 才会被 AOP 机制扫描到
 * （见 DynamicDataSourceAutoConfiguration 里的 dsAspect()）。
 */
@Aspect
public class DSAspect {

    /**
     * 环绕通知：包住整个方法，前后都能插逻辑，还能拿到返回值。
     * <p>
     * 切点表达式 @annotation(ds)：
     *   括号里的 ds 是"方法参数名引用"，指代下面那个 DS ds 参数。
     *   Spring 会把被拦截方法上那个 @DS 注解的实例传进来，所以能读到 ds.value()。
     * <p>
     * 三种常见写法的区别：
     *   @annotation(ds)                        参数名引用，能拿到注解实例 ✅ 本类用的
     *   @annotation('ds')                      加了引号 → 当成字符串/类型名找注解 → 永不匹配 ❌
     *   @annotation(com.minids.annotation.DS)  写全类名 → 只匹配，参数绑不上 ❌
     * <p>
     * 为什么必须是 @Around，不能用 @After：
     *   @After 在方法抛异常时不会执行，ThreadLocal 清不掉，
     *   会污染复用这条线程的下一个请求（Tomcat 线程池）。
     *   @Around 里有 try-finally，正常返回和抛异常都能保证清理。
     * <p>
     * 为什么 throws Throwable 而不是 Exception：
     *   proceed() 的签名就是 throws Throwable，原方法可能抛 Error。
     * <p>
     * 参数顺序：ProceedingJoinPoint 必须放第一个，这是 Spring AOP 的约定。
     */
    @Around("@annotation(ds)")
    public Object switchDataSource(ProceedingJoinPoint joinPoint, DS ds) throws Throwable {

        String value = ds.value();
        try {
            // 执行前：把 key 放进当前线程的"储物柜"
            DataSourceContextHolder.set(value);
            // proceed() = 去执行原方法。业务方法里的 getConnection() 会读到上面这个 key
            return joinPoint.proceed();
        } finally {
            // 执行后：无论成功还是抛异常，都必须清空。这是动态数据源最容易出生产事故的地方
            DataSourceContextHolder.clear();
        }


    }
}
