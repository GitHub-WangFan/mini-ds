package com.minids;

import com.minids.annotation.DS;

import java.lang.reflect.Method;

/**
 * 理解反射
 */
public class DemoReflect {

    @DS("slave")
    public void testDemo1(){
        System.out.println("---进入注解注释的方法----");
    }

    public static void main(String[] args) throws Exception{
        //1.先获取类实例
        Class<DemoReflect> demoClass = DemoReflect.class;
        System.out.println("该类名为" + demoClass);

        //2.获取方法
        Method demoMethod = demoClass.getMethod("testDemo1");
        System.out.println("该方法名为" + demoMethod);

        //3.根据类实例创建实例
        DemoReflect demoReflect = demoClass.getDeclaredConstructor().newInstance();

        //4.通过方法来进行反射
        demoMethod.invoke(demoReflect);

    }

}
