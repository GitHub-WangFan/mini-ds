package com.minids.annotation;

import java.lang.annotation.*;

/**
 * 数据源切换注解：标注在方法或类上，表示这段代码要走哪个数据源。
 * <p>
 * 语法要点：
 *  1. 用 @interface 声明注解（本质是一个继承 Annotation 的接口）
 *  2. 元注解（描述注解的注解）：
 *     2.1 @Target      指定注解能标在哪些位置（此处：类/接口、方法）
 *     2.2 @Retention   指定保留策略；必须是 RUNTIME，运行时反射才读得到，
 *                      写成 SOURCE/CLASS 会导致 getAnnotation 恒返回 null
 *     2.3 @Documented  让注解出现在 javadoc 里（仅影响文档，不影响运行）
 *  3. 定义属性：String value() default "master";  属性名叫 value 时使用可简写 @DS("slave")
 *     默认值 "master" 必须与配置文件里默认数据源的 key 一致
 */

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DS {

    String value() default "master";
}
