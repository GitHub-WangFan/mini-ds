# mini-ds

一个极简的 Spring Boot 动态数据源 Starter —— 在方法或类上标一个 `@DS("slave")`，这段代码里的数据库操作就自动切换到指定数据源，业务代码零侵入。

## 特性

- **注解切换**：`@DS` 支持方法级和类级，方法级优先于类级
- **自动装配**：引入依赖 + 写好 yml，启动即用，无需任何 Java 配置代码
- **基于 `AbstractRoutingDataSource`**：Spring 官方的路由数据源模板，稳定可靠
- **自带连接池**：通过 `DataSourceBuilder` 自动选择 classpath 上的连接池实现（Spring Boot 默认 HikariCP）
- **线程安全**：数据源 key 存于 `ThreadLocal`，切面 `finally` 保证清理，不会污染复用线程的后续请求
- **礼貌退让**：使用方自定义了 `DataSource` Bean 时，本 Starter 自动让位（`@ConditionalOnMissingBean`）

## 快速开始

### 1. 获取依赖

项目尚未发布到 Maven 中央仓库，可克隆后安装到本地仓库：

```bash
git clone https://github.com/<你的用户名>/mini-ds.git
cd mini-ds
mvn install
```

在使用方项目的 `pom.xml` 中引入：

```xml
<dependency>
    <groupId>com.minids</groupId>
    <artifactId>mini-ds</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置数据源

```yaml
mini:
  ds:
    primary: master          # 未标注解时使用的默认数据源
    datasource:
      master:
        driver-class-name: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://localhost:3306/master_db
        username: root
        password: root
      slave:
        driver-class-name: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://localhost:3306/slave_db
        username: root
        password: root
```

### 3. 使用 `@DS` 注解

```java
// 方法级：只有这个方法走从库
@DS("slave")
public List<Order> queryOrders() {
    return orderMapper.selectList(null);
}

// 类级：整个类默认走从库
@DS("slave")
public class ReportService {
    public List<Report> list() { ... }          // 走 slave

    @DS("master")
    public void save(Report report) { ... }     // 方法级覆盖类级，走 master
}

// 不标注解 → 走 mini.ds.primary 指定的默认数据源
public void write() { ... }
```

## 工作原理

```
@DS("slave") 注解
    │
    ▼
DSAspect（@Around 环绕通知）
    │  切点：@annotation(DS) || @within(DS)，方法级优先于类级
    │  执行前 DataSourceContextHolder.set("slave")（ThreadLocal）
    ▼
业务方法执行
    │  MyBatis / JdbcTemplate 调 getConnection()
    ▼
RoutingDataSource（继承 AbstractRoutingDataSource）
    │  determineCurrentLookupKey() 读 ThreadLocal → "slave"
    │  从 targetDataSources Map 里取出 slave 对应的真实数据源
    ▼
slave 数据源返回连接
    │
    ▼
方法结束，切面 finally 中 DataSourceContextHolder.clear()
```

几个关键设计：

| 设计点 | 说明 |
|---|---|
| ThreadLocal 存 key | 同一线程内贯穿整个调用链，线程之间互不干扰 |
| `finally` 清理 | 方法抛异常也能清理，避免 Tomcat 复用线程读到残留 key（高频生产事故点） |
| 切点不绑定变量 | 用 `@annotation(全类名)` 只筛选不绑定，规避 `\|\|` 表达式上的 inconsistent binding 问题 |
| `@AutoConfigureBefore(DataSourceAutoConfiguration.class)` | 抢在 Spring Boot 默认数据源装配之前注册，否则 `@ConditionalOnMissingBean` 会让位 |

## 项目结构

```
com.minids
├── annotation
│   └── DS.java                        # @DS 注解定义
├── context
│   └── DataSourceContextHolder.java   # ThreadLocal 持有当前线程的数据源 key
├── config
│   ├── RoutingDataSource.java         # 继承 AbstractRoutingDataSource，10 行核心路由
│   ├── DataSourceProperties.java      # @ConfigurationProperties 绑定 mini.ds.* 配置
│   └── DynamicDataSourceAutoConfiguration.java  # 自动装配入口
├── aop
│   └── DSAspect.java                  # 拦截 @DS，set / clear ThreadLocal
└── resources
    └── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 测试

四个测试对应三个层次，全部通过：

| 测试类 | 层次 | 验证内容 |
|---|---|---|
| `RoutingDataSourceTest` | 零件层 | 路由逻辑本身：key 为 null 走默认，设了 key 走对应库 |
| `AutoConfigurationTest` | 装配层 | 只写 yml，容器自动装配出 RoutingDataSource 且路由正确 |
| `DSAspectTest.shouldSwitchByMethodAnnotation` | 使用层 | 方法级 `@DS` 生效，执行后 ThreadLocal 已清理 |
| `DSAspectTest.shouldSwitchByClassAnnotation` | 使用层 | 类级 `@DS` 生效，方法级可覆盖类级 |

运行：

```bash
mvn test
```

测试使用 H2 内存数据库（`MODE=MySQL`），无需安装任何数据库。

## 环境要求

- Java 17+
- Spring Boot 3.x（基于 3.3.5 开发）

## Roadmap

- [ ] 运行时动态注册数据源（不重启应用新增数据源）
- [ ] 支持嵌套切换 / 分组（如 `slave_1`、`slave_2` 轮询负载均衡）
- [ ] 发布到 Maven 中央仓库

## License

[MIT](LICENSE)
