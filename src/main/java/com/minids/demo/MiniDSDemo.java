package com.minids.demo;

import com.minids.annotation.DS;
import com.minids.context.DataSourceContextHolder;

import java.lang.reflect.Method;

/**
 * 手动模拟切面（DSAspect）做的事：反射读 @DS → 存入 ThreadLocal → 执行业务方法 → 清理。
 * <p>
 * 说明：这里在 main 单线程里手动模拟，真实项目由 DSAspect 自动完成，业务代码无感知。
 * 预期输出：annotationMethod 取到 "slave"；normalMethod 取到 null（表示走默认数据源）。
 */
public class MiniDSDemo {

    @DS("slave")
    public void annotationMethod(){
        System.out.println("标注了 @DS 的方法，当前数据源 key = " + DataSourceContextHolder.get());
    }

    public void normalMethod(){
        System.out.println("未标注 @DS 的方法，当前数据源 key = " + DataSourceContextHolder.get() + "（null 表示走默认数据源）");
    }

    public static void main(String[] args) throws Exception{
        //1. 拿到类的 Class 对象（反射入口）
        Class<MiniDSDemo> demoClass = MiniDSDemo.class;

        // ===== 场景一：标注了 @DS 的方法 =====
        //2. 按方法名取到 Method 对象
        Method annotationMethod = demoClass.getMethod("annotationMethod");
        //3. 从 Method 上取出 @DS 注解实例（没标则返回 null）
        DS annotation = annotationMethod.getAnnotation(DS.class);
        if (annotation != null) {
            String value = annotation.value();
            //4. 把数据源 key 存入当前线程的 ThreadLocal（真实场景由切面完成）
            DataSourceContextHolder.set(value);
        }

        //5. 拿到无参构造器并创建实例
        MiniDSDemo demoInstance = demoClass.getDeclaredConstructor().newInstance();
        //6. 用 Method.invoke 在实例上执行方法
        annotationMethod.invoke(demoInstance);
        //7. 清理当前线程的 ThreadLocal（线程池复用时不清会读到上一个请求的残留值）
        DataSourceContextHolder.clear();



        // ===== 场景二：未标注 @DS 的方法 =====
        Method normalMethod = demoClass.getMethod("normalMethod");
        //未 set 过任何值，ThreadLocal 为空，get() 返回 null → 表示走默认数据源
        MiniDSDemo normalInstance = demoClass.getDeclaredConstructor().newInstance();
        normalMethod.invoke(normalInstance);

        //确认当前代码跑在哪条线程上（JVM 启动时创建的第一条线程，名为 main）
        System.out.println("当前线程：" + Thread.currentThread().getName());

    }
}
