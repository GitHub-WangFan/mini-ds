package com.minids;

import com.minids.annotation.DS;
import com.minids.context.DataSourceContextHolder;

import java.lang.reflect.Method;

public class MiniDSDemo {

    @DS("slave")
    public void annotationMethod(){
        System.out.println("使用注解的方法,当前值为" + DataSourceContextHolder.get());
    }

    public void normalMethod(){
        System.out.println("未使用注解的方法,当前值为" + DataSourceContextHolder.get());
    }

    public static void main(String[] args) throws Exception {
        //1.先获取当前类
        Class<MiniDSDemo> demoClass = MiniDSDemo.class;

        //读取注解
        Method annotationMethod = demoClass.getMethod("annotationMethod");
        DS annotation = annotationMethod.getAnnotation(DS.class);
        //将读取的注解存入线程池中
        if (annotation != null) {
            DataSourceContextHolder.set(annotation.value());
        }
        //通过反射执行
        //先创建实例
        MiniDSDemo annotationDemo = demoClass.getDeclaredConstructor().newInstance();
        annotationMethod.invoke(annotationDemo);
        DataSourceContextHolder.clear();


        //执行未添加注解的方法
        Method normalMethod = demoClass.getMethod("normalMethod");
        MiniDSDemo normalDemo = demoClass.getDeclaredConstructor().newInstance();
        normalMethod.invoke(normalDemo);

    }
}
