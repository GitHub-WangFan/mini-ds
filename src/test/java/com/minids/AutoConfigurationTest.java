package com.minids;

import com.minids.config.DynamicDataSourceAutoConfiguration;
import com.minids.context.DataSourceContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证"自动配置真的生效"：全程不手动 new 任何东西，只启动一个最小 Spring 容器，
 * 看它能不能自己把 RoutingDataSource 装配出来。
 * <p>
 * 和 RoutingDataSourceTest 的分工：
 *   RoutingDataSourceTest   端到端验证：建表插数查数据，验证路由逻辑本身
 *   AutoConfigurationTest   装配验证：只看"容器里有没有、连的是哪个库"
 * <p>
 * 两个注解的分工：
 *   @SpringJUnitConfig(classes = ...)   不启动整个应用，只建"最小容器"，里面只有指定配置类
 *   ConfigDataApplicationContextInitializer
 *                                       @SpringJUnitConfig 默认不读 application.yml（那是
 *                                       @SpringBootTest 的待遇），这个初始化器手动装载 yml
 */
@SpringJUnitConfig(
        classes = DynamicDataSourceAutoConfiguration.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
public class AutoConfigurationTest {

    /**
     * @Autowired：容器自己把 DataSource Bean 塞进来（按类型匹配）。
     * 如果容器里没有 DataSource，这里直接报 No qualifying bean——今天遇到过的就是这个错
     */
    @Autowired
    private DataSource dataSource;

    /**
     * 三段断言，对应自动配置的三个问题：
     *   1. 装配出来的对不对（类型是路由数据源）
     *   2. 默认路由对不对（没设 key 走 master）
     *   3. 指定路由对不对（设了 key 走 slave）
     */
    @Test
    void shouldAutoConfigureRoutingDataSource() throws SQLException {
        // 1. 容器里拿到的确实是路由数据源，不是 Boot 默认的普通连接池
        assertInstanceOf(AbstractRoutingDataSource.class,dataSource);

        // 2. 默认走 master。
        //    conn.getMetaData().getURL() 是 JDBC 规范方法，返回这条连接实际连的 URL——
        //    验证路由生效最直接的证据，不用建表插数据
        try (Connection conn = dataSource.getConnection()){
            assertTrue(conn.getMetaData().getURL().contains("master"));
        }

        // 3. 设了 key 就走 slave。finally 里必须清掉：同 JVM 的其他测试/线程可能复用这条线程
        DataSourceContextHolder.set("slave");
        try (Connection conn = dataSource.getConnection()){
            assertTrue(conn.getMetaData().getURL().contains("slave"));
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
