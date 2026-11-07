package com.minids;

import com.minids.annotation.DS;

import java.lang.reflect.Method;

/**
 * 反射四步：Class 对象 → Method → 实例 → invoke 调用。
 * 这是 mini-ds 切面的底层原理：运行时才按名字找到方法、读出注解、执行。
 */
public class DemoReflect {

    @DS("slave")
    public void testDemo1(){
        System.out.println("---方法体执行了---");
    }

    public static void main(String[] args) throws Exception{
        //1. 获取类的 Class 对象（类的运行时信息，反射的入口）
        //   两种写法等价：Class.forName 用字符串（框架常用，类名可来自配置），Xxx.class 编译期写死
        Class<?> demoClass = Class.forName("com.minids.DemoReflect");
//        Class<DemoReflect> demoClass = DemoReflect.class;
        System.out.println("该类名为" + demoClass);

        //2. 按方法名（字符串）取到 Method 对象
        Method demoMethod = demoClass.getMethod("testDemo1");
        System.out.println("该方法名为" + demoMethod);

        //3. 拿到无参构造器并创建实例（Class.newInstance() 自 JDK 9 起已废弃）
//        Object o = demoClass.newInstance();
//        System.out.println(o);
        DemoReflect demoReflect = (DemoReflect) demoClass.getDeclaredConstructor().newInstance();
        System.out.println(demoReflect);
        //4. 用 Method.invoke(实例) 执行方法（当前注释掉，需要时打开）
//        demoMethod.invoke(demoReflect);

    }

}
