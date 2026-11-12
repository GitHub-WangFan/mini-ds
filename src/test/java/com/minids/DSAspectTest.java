package com.minids;

import com.minids.annotation.DS;
import com.minids.config.DynamicDataSourceAutoConfiguration;
import com.minids.context.DataSourceContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证"注解真的能切库"：不手动设置 ThreadLocal，只靠 @DS 注解，看连接实际走到哪个库。
 * <p>
 * 三个测试类的分工，正好是三层：
 *   RoutingDataSourceTest   零件层：路由逻辑本身（手动 new + 手动 set）
 *   AutoConfigurationTest   装配层：自动配置把 Bean 装出来了（不碰注解）
 *   DSAspectTest            使用层：用户视角，标个注解就切库（本类）
 * <p>
 * 注意：@SpringJUnitConfig 不加载 Boot 自动配置，所以 AopAutoConfiguration 不会生效，
 * 必须在这个类里手动写 @EnableAspectJAutoProxy，否则切面根本不会被应用。
 */
@SpringJUnitConfig(
        classes = {DynamicDataSourceAutoConfiguration.class,DSAspectTest.TestConfig.class},
        initializers = ConfigDataApplicationContextInitializer.class
)
public class DSAspectTest {

    /**
     * 测试专用配置：注册一个假的业务类，并手动打开 AOP。
     * <p>
     * proxyTargetClass = true：
     *   DemoService 没有实现任何接口，必须用 CGLIB 生成子类来做代理。
     *   默认值是 false（走 JDK 动态代理，要求目标类有接口），这里不改就会失败。
     *   Spring Boot 在生产环境默认就是 true（spring.aop.proxy-target-class 默认开启）。
     */
    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig{

        @Bean
        public DemoService demoService(){
            return new DemoService();
        }


    }

    /**
     * 模拟测试业务 模拟使用注解 与 不使用注解
     * <p>
     * 这个类不是业务代码，是"靶子"：一个方法标 @DS，一个不标，用来对比路由结果。
     * 由 TestConfig 的 @Bean 创建，所以它 @Autowired 的字段能被 Spring 注入；
     * 又因为方法上匹配了切点，Spring 返回给调用方的是它的 CGLIB 代理对象。
     */
    static class DemoService{

        @Autowired
        private DataSource dataSource;



        @DS("slave")
        public String withAnnotation() throws SQLException {
            return getCurrentUrl();
        }

        public String withoutAnnotation() throws SQLException {
            return getCurrentUrl();
        }

        /**
         * 内部调用不经过代理（this.getCurrentUrl()），但这不影响结果：
         * 切面是在 withAnnotation() 这一层设置的 ThreadLocal，
         * 内部方法拿 connection 时读的仍是同一个线程的 ThreadLocal
         */
        private String getCurrentUrl() throws SQLException {
            try (Connection conn = dataSource.getConnection()){
                return conn.getMetaData().getURL();
            }
        }
    }

    @Autowired
    private DemoService demoService;

    @Test
    void shouldSwitchByAnnotation() throws SQLException {

        // 1. 标了 @DS("slave") → 切面把 key 设成 slave → 连到从库
        assertTrue(demoService.withAnnotation().contains("slave"));

        // 2. 没标注解 → ThreadLocal 为空 → 走默认主库
        assertTrue(demoService.withoutAnnotation().contains("master"));

        // 3. 关键：方法执行完切面已经 clear，ThreadLocal 必须为空，
        //    否则复用这条线程的下一个请求会读到残留的 "slave"
        assertNull(DataSourceContextHolder.get());
    }
}
