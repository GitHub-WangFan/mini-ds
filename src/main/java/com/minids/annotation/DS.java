package com.minids.annotation;

import java.lang.annotation.*;

/**
 * 定义注解：
 *  1.使用@interface关键字声明注解
 *  2.配置元注解（描述注解的注解）
 *      2.1 @Target 指定该注解可以放在哪个位置上
 *      2.2 @Retention 指定注解的保留策略 即这个注解保留到什么时候
 *  3.配置Documented 用于标注其他注解 让注解出现在生成的 javadoc 里
 *  4.定义注解的属性
 */

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {

    String value() default "master";
}
