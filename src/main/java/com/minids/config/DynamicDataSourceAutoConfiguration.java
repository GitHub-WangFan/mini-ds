package com.minids.config;

import com.minids.aop.DSAspect;
import com.minids.config.DataSourceProperties.DataSourceProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动配置类：让使用者"只写 yml、零 Java 代码"就能拿到路由数据源。
 * <p>
 * 生效链路（Spring Boot 启动时）：
 *   1. 读取所有 jar 的 META-INF/spring/...AutoConfiguration.imports 清单（一行一个配置类全名）
 *   2. 对清单里的每个类评估 @ConditionalXxx 条件，不满足的直接跳过（静默，无日志）
 *   3. 满足的当普通配置类处理：执行 @Bean 方法，把返回值注册进容器
 * <p>
 * 本类就是清单文件里的那一行：com.minids.config.DynamicDataSourceAutoConfiguration
 * <p>
 * 排查"配置了却不生效"的通用武器：启动参数加 -Ddebug=true，
 * Spring 会打印条件评估报告（CONDITIONS EVALUATION REPORT），每个类匹配/不匹配及原因都列出来。
 */
@AutoConfiguration
// Boot 3.0 专供自动配置的注解，本质是 @Configuration(proxyBeanMethods = false)：
// 自动配置类之间不会互相调用 @Bean 方法，不需要 CGLIB 代理，关掉能加快启动
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
// 排序：Boot 自带的 DataSourceAutoConfiguration 和本类都带 @ConditionalOnMissingBean(DataSource)，
// 两个都"有人就不建"时，谁先注册谁赢。不声明先跑，顺序不确定，可能被 Boot 抢先
@EnableConfigurationProperties(DataSourceProperties.class)
// 把 @ConfigurationProperties 类注册成 Bean 并激活属性绑定（经纪人角色）。
// 没有这行，DataSourceProperties 不进容器，下面 dataSource() 的参数注入会直接失败。
// 注意：Starter 里不能改用 @Component——使用方只扫描他们自己的包，扫不到 com.minids
@ConditionalOnProperty(prefix = "mini.ds", name = "primary")
// 只有 yml 配了 mini.ds.primary 才生效。防止没配的项目引了 starter 被硬塞一个空路由数据源。
// 坑：只能指向"叶子属性"。datasource 是中间节点（文件夹），拍平后不存在，永远匹配 false
public class DynamicDataSourceAutoConfiguration {

    /**
     * 组装路由数据源：把 yml 里 mini.ds.datasource.* 的每个库变成 DataSource，再装进 RoutingDataSource。
     * <p>
     * 做的事和 RoutingDataSourceTest 里手动写的完全一样，区别只是：
     * 那里是测试代码手动 new，这里是 Spring 启动时自动调用，使用方无感。
     * <p>
     * 三个注解的分工：
     *   @Bean                        方法返回值注册进容器，方法名就是 Bean 名
     *   @Primary                     容器里有多个 DataSource 时，注入默认选这个
     *   @ConditionalOnMissingBean    使用者自己定义了 DataSource 就让位——Starter 的基本礼貌
     * <p>
     * 参数 properties 由 Spring 注入（依赖注入）：容器里已有 DataSourceProperties Bean，直接塞进来
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(DataSourceProperties properties) {
        // 1. 把 yml 里的每个库都转成真实 DataSource，key 保持 yml 里的名字（master / slave）
        Map<Object, Object> targets = new LinkedHashMap<>();
        properties.getDatasource().forEach((key,prop) -> {
            targets.put(key,buildDataSource(prop));
        });

        // 2. 组装路由数据源（同测试代码的四步）
        RoutingDataSource dataSource = new RoutingDataSource();
        dataSource.setTargetDataSources(targets);
        // 兜底库：ThreadLocal 里没有 key 时走它。参数必须是 DataSource 对象，不能传 "master" 字符串
        dataSource.setDefaultTargetDataSource(targets.get(properties.getPrimary()));
        // 手动 new 的必须自己调：把 targets 加工成内部真正使用的结构，Spring 容器里这步是自动的
        dataSource.afterPropertiesSet();

        return dataSource;
    }

    /**
     * 把"一个库的连接信息"变成"一个可用的 DataSource"。
     * <p>
     * 参数是内部类 DataSourceProperty（一张名片：driverClassName/url/username/password 四件套），
     * 不是外层 DataSourceProperties（整本通讯录）——曾经因为参数类型传错调不到 getDriverClassName()
     * <p>
     * DataSourceBuilder 会看 classpath 上有什么连接池实现，自动挑一个：
     * pom 里有 spring-boot-starter-jdbc（自带 HikariCP），所以生产环境直接就是带连接池的。
     * 比测试里用的 DriverManagerDataSource（每次真新建连接、无池）高一个档次
     */
    private DataSource buildDataSource(DataSourceProperty prop) {
        return DataSourceBuilder.create()
                .driverClassName(prop.getDriverClassName())
                .url(prop.getUrl())
                .username(prop.getUsername())
                .password(prop.getPassword())
                .build();
    }


    /**
     * 把切面注册成 Bean。
     * <p>
     * 为什么必须注册：@Aspect 只是"告诉 Spring 这是个切面"的标记，类本身还得进容器，
     * 才会被 AOP 机制扫描到。不注册的话注解照样能编译，但运行时悄无声息地不生效。
     * <p>
     * 返回值类型写具体的 DSAspect 而不是 Object：
     * @ConditionalOnMissingBean 是靠"方法的返回类型"来判断容器里有没有同类 Bean 的。
     * <p>
     * 不需要 @EnableAspectJAutoProxy：Boot 的 AopAutoConfiguration 会自动开启
     * （前提是 pom 里有 spring-boot-starter-aop）。
     * 但测试里 @SpringJUnitConfig 不加载 Boot 自动配置，所以 DSAspectTest 必须手动加。
     */
    @Bean
    @ConditionalOnMissingBean
    public DSAspect dsAspect(){
        return new DSAspect();
    }
}
