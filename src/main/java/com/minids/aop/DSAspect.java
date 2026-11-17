package com.minids.aop;

import com.minids.annotation.DS;
import com.minids.context.DataSourceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

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
     * 切点表达式 @annotation(...) || @within(...)：
     *   @annotation(全类名)  匹配"方法上"标了 @DS 的方法
     *   @within(全类名)      匹配"类上"标了 @DS 的类里的所有方法
     *   两个用 || 连起来 = 方法级、类级都支持。
     * <p>
     * 为什么括号里写全类名，而不写参数名（@annotation(ds)）：
     *   写参数名会让 Spring 把注解实例"绑定"到切面方法参数上，一个变量在两个
     *   || 分支里绑定，会出现"一边绑了、一边没绑"的歧义（inconsistent binding），
     *   启动就可能报错；就算跑起来也可能拿到 null。
     *   所以这里让切点只负责"筛"，取哪个注解由下面的反射代码决定。
     * <p>
     * 括号里只能写类型名，不能写 DS.class：
     *   切点表达式是一段被 AspectJ 解析的"文本"，不参与 Java 编译，
     *   写 DS.class 会报 Pointcut is not well-formed。
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
    @Around("@annotation(com.minids.annotation.DS) || @within(com.minids.annotation.DS)")
    public Object switchDataSource(ProceedingJoinPoint joinPoint) throws Throwable {

        // getSignature() 返回的是 Signature 接口，方法级切点下它的实际类型是 MethodSignature，
        // 只有转成 MethodSignature 才能拿到 Method 对象（Signature 本身只有名字字符串）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 优先级：方法上的 @DS > 类上的 @DS
        // 类上写粗粒度的默认值，个别方法想例外就单独标，这是业界惯例
        DS ds = method.getAnnotation(DS.class);
        if (ds == null) {
            ds = method.getDeclaringClass().getAnnotation(DS.class);
        }

        // 兜底：理论上切点已经保证注解存在，但 @within 在继承场景下可能命中
        // 一个"自己没标、父类标了"的子类方法（取决于 @Inherited），这里防 NPE
        if (ds == null) {
            return joinPoint.proceed();
        }

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
