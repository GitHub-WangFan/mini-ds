# mini-ds 学习笔记

> 迷你动态数据源 Starter —— 通过注解切换数据源
> 本文件是我自己的学习笔记，记录每一段代码背后涉及的原理。

---

## 目录

1. [项目定位与当前进度](#一项目定位与当前进度)
2. [测试代码逐行标注](#二测试代码逐行标注)
3. [知识点一：JDBC 是什么](#三知识点一jdbc-是什么)
4. [知识点二：JDBC URL 拆解](#四知识点二jdbc-url-拆解)
5. [知识点三：H2 内存数据库](#五知识点三h2-内存数据库)
6. [知识点四：DataSource 不是数据库](#六知识点四datasource-不是数据库)
7. [知识点五：try-with-resources](#七知识点五try-with-resources)
8. [知识点六：AbstractRoutingDataSource](#八知识点六abstractroutingdatasource)
9. [知识点七：断言与 @Test](#九知识点七断言与-test)
10. [一次 getConnection 的完整旅程](#十一一次-getconnection-的完整旅程)
11. [踩过的坑清单](#十二踩过的坑清单)
12. [学习资源](#十三学习资源)
13. [Day 8：自动配置（2026-11-11）](#十五day-8自动配置2026-11-11)
14. [Day 9：AOP 切面（2026-11-12）](#十六day-9aop-切面2026-11-12)
15. [Day 10：类级注解与切点表达式（2026-11-17）](#十七day-10类级注解与切点表达式2026-11-17)
16. [下一步](#十八下一步)

---

## 十五、Day 8：自动配置（2026-11-11）

> 今天的主线：**Spring 容器怎么替你干活**。昨天全是"自己动手 new"的世界，今天进入"登记注册、按需领取"的世界。

### Day 8 交付物

| 文件 | 说明 |
|---|---|
| `config/DynamicDataSourceAutoConfiguration.java` | 自动配置类 |
| `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 清单文件（文件名是 Boot 硬编码的） |
| `test/.../AutoConfigurationTest.java` | 最小容器验证 |

### 1. Bean 与 @Bean

- **是什么**：交给 Spring 容器保管的对象叫 Bean。`@Bean` 方法 = "我返回的这个对象你保管，谁要给谁"
- **怎么运作**：启动时扫配置类 → 执行 @Bean 方法 → 返回值存进一张表（名字 → 对象），这张表就是容器
- **对应代码**：`dataSource()` 方法，方法名就是 Bean 的名字

### 2. 依赖注入（DI）：方法参数谁给的

```java
public DataSource dataSource(DataSourceProperties properties)  ← 没人传，Spring 自己塞
```

三步：容器建好 → 发现方法要 `DataSourceProperties` 类型 → 去表里查 → 有就塞进来。

**控制反转（IOC）** = 对象的创建权从"你"反转到"容器"。昨天手动 new 的每一行，今天都被容器接管。

### 3. @ConfigurationProperties 和 @EnableConfigurationProperties 是一对

| 注解 | 角色 |
|---|---|
| `@ConfigurationProperties`（类上） | 演员：只声明"我的字段对应 yml 的 mini.ds 前缀"，**自己不会注册成 Bean** |
| `@EnableConfigurationProperties(类.class)`（配置类上） | 经纪人：真正把演员注册进容器、激活绑定 |

**为什么 Starter 里不用 @Component 代替**：@Component 靠包扫描，而使用方只扫他们自己的包，扫不到你的 `com.minids`。所以 Starter 必须用 @EnableConfigurationProperties 显式注册。面试加分点。

### 4. 条件注解家族 @ConditionalOnXxx

满足条件才生效，不满足**当不存在**。两个关键认知：

**坑（今天踩过）**：`@ConditionalOnProperty` 只能看**叶子属性**，不能看 yml 的中间节点：

```
mini.ds.datasource   ← 文件夹（中间节点），拍平后不存在 → 永远匹配失败
mini.ds.primary      ← 叶子，有值 → 能匹配
```

**静默失败**：条件不满足不报错不打日志，Bean 悄悄消失。调试：启动参数 `-Ddebug=true` 打印**条件评估报告**。

**@ConditionalOnMissingBean** = "有人建过我就不建"，Starter 的基本礼貌，Spring Boot 组件能随意组合靠的就是它。

### 5. 自动配置链路（面试必背）

```
① Spring Boot 启动
② 读取所有 jar 的 META-INF/spring/...AutoConfiguration.imports（一行一个全限定类名）
③ 逐个评估 @ConditionalXxx 条件，不满足跳过
④ 满足的当 @Configuration 处理：执行 @Bean 方法，注册进容器
```

细节：

- **@AutoConfiguration**：Boot 3.0 专供自动配置的注解，本质 `@Configuration(proxyBeanMethods = false)`——自动配置类之间不互相调用 @Bean 方法，不需要 CGLIB 代理
- **@AutoConfigureBefore**：两个配置类都带 @ConditionalOnMissingBean(DataSource) 时，**谁先注册谁赢**，所以要声明"我比 Boot 自带的先跑"
- **这套机制的思想是 SPI**：框架定标准，实现方在 jar 里放"自荐信"，框架启动时收集。JDBC 驱动发现、SLF4J 找日志实现，都是这个套路

### 6. DataSourceBuilder 与连接池

`DataSourceBuilder.create()...build()` 会看 classpath 上有什么连接池实现，自动挑一个（有 HikariCP 就选 Hikari）。

| | DriverManagerDataSource | HikariDataSource |
|---|---|---|
| getConnection | 真新建连接 | 池里借，用完还 |
| 适用 | 测试造假库 | 生产 |
| 项目里 | RoutingDataSourceTest | AutoConfigurationTest（经 DataSourceBuilder） |

### 7. 测试侧新工具

- **@SpringJUnitConfig(classes = X.class)**：不启动整个应用，只建"最小容器"——只有指定配置类。测自动配置的标准姿势
- **ConfigDataApplicationContextInitializer**：@SpringJUnitConfig 默认不读 yml（那是 @SpringBootTest 的待遇），这个初始化器手动装载 yml
- **conn.getMetaData().getURL()**：JDBC 规范方法，返回连接实际 URL。验证路由生效最直接的证据，不用建表插数据。昨天的测试是"端到端"，今天这个是"只看连接"

### 8. Day 8 踩坑记录

| 坑 | 原因 | 修法 |
|---|---|---|
| `buildDataSource(DataSourceProperties prop)` 调不到 `getDriverClassName()` | 参数用了外层类，方法在内部类上 | 参数类型改 `DataSourceProperties.DataSourceProperty` |
| 容器里没有 DataSource Bean，测试报 UnsatisfiedDependency | `@ConditionalOnProperty(name="datasource")` 指向中间节点，条件永远 false，整个配置类被跳过 | 改 `name="primary"` |
| `@AutoConfigureBefore` 括号里填了自己的类名 | "自己排自己前面"无意义 | 应填 `DataSourceAutoConfiguration.class` |

### 9. Day 8 学习资源

| 顺序 | 主题 | 搜索关键词 | 达标标准 |
|---|---|---|---|
| 1 | Spring IoC 与依赖注入 | `Spring 依赖注入 入门` | 能说清 Bean 是什么、@Bean 参数谁给的 |
| 2 | 自动配置原理（**面试必考**） | `Spring Boot 自动配置原理` | 能默画上面 4 步链路 |
| 3 | 条件注解 | `@ConditionalOnProperty @ConditionalOnMissingBean 用法` | 知道叶子/文件夹区别 + debug 报告 |
| 4 | @ConfigurationProperties | `Spring Boot ConfigurationProperties 绑定` | 知道和 @EnableConfigurationProperties 是一对 |

### 10. Day 8 完成后的一句话总结

> 这个 Starter 通过 `@AutoConfiguration` + `AutoConfiguration.imports` 注册，使用方只要在 yml 里配好数据源清单，Spring Boot 启动时自动装配出一个带 HikariCP 连接池的 `AbstractRoutingDataSource`，业务代码零侵入。

**下一步（Day 9）**：写 `DSAspect`（AOP），让 `@DS("slave")` 注解真正生效。

---

## 一、项目定位与当前进度

**一句话**：做一个 Spring Boot Starter，让使用者只要在方法上写 `@DS("slave")`，这个方法里的数据库操作就自动走 slave 库，不用改任何业务代码。

**为什么需要它**：读写分离、多租户、冷热数据分离这些场景，业务代码里不想写 `if (readOnly) useSlave()`。

**已完成**（截至 2026-11-17）：

| 模块 | 类 | 状态 |
|---|---|---|
| annotation | `DS` | ✅ 注解定义完成 |
| context | `DataSourceContextHolder` | ✅ ThreadLocal 存 key |
| config | `RoutingDataSource` | ✅ 继承 AbstractRoutingDataSource |
| config | `DataSourceProperties` | ✅ 读 application.yml 配置 |
| config | `DynamicDataSourceAutoConfiguration` | ✅ 自动配置完成（11-11） |
| aop | `DSAspect` | ✅ 切面完成（11-12），支持方法级 + 类级（11-17） |
| resources | `AutoConfiguration.imports` | ✅ 已创建（11-11） |
| test | `RoutingDataSourceTest` | ✅ 测试通过 |
| test | `AutoConfigurationTest` | ✅ 测试通过（11-11） |
| test | `DSAspectTest` | ✅ 2 个测试通过（11-17 补类级场景） |

**当前状态**：功能已全部闭环（4 个测试全绿）。代码层面没有遗留缺口，剩下的是 README 和上 GitHub。

---

## 二、测试代码逐行标注

文件：`src/test/java/com/minids/RoutingDataSourceTest.java`

```java
// 造一个"指向某个 H2 内存库"的数据源对象。
// 注意：此时只是造了工厂，还没有真正连数据库。
private DataSource createDs(String name) {
    return new DriverManagerDataSource(
            "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
}

// 给指定的库建表 + 插一条数据，数据内容用来区分是哪个库
private void prepareData(String dbName, String value) throws SQLException {
    try (Connection conn = createDs(dbName).getConnection();   // 这里才真正连库
         Statement st = conn.createStatement()) {
        st.execute("CREATE TABLE TESTTABLE (test varchar(50))");
        st.execute("INSERT INTO TESTTABLE VALUES ('" + value + "')");
    }   // 代码块结束自动 close，不用写 finally
}

@Test
void routeToCorrectDataSource() throws SQLException {
    // 1. 两个库各准备一条不同内容的数据
    prepareData("master", "from-master");
    prepareData("slave",  "from-slave");

    // 2. 组装路由数据源
    RoutingDataSource routingDataSource = new RoutingDataSource();
    Map<Object, Object> targets = new HashMap<>();
    targets.put("master", createDs("master"));
    targets.put("slave",  createDs("slave"));
    routingDataSource.setTargetDataSources(targets);          // 塞映射表
    routingDataSource.setDefaultTargetDataSource(createDs("master"));  // 传对象！不是字符串
    routingDataSource.afterPropertiesSet();                   // 手动 new 的必须自己调

    // 场景一：ThreadLocal 里没东西 → key 为 null → 走默认库
    try (Connection conn = routingDataSource.getConnection();
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT test FROM TESTTABLE")) {
        rs.next();
        assertEquals("from-master", rs.getString(1));
    }

    // 场景二：往 ThreadLocal 里塞 "slave" → 路由到 slave 库
    DataSourceContextHolder.set("slave");
    try (Connection conn = routingDataSource.getConnection();
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT test FROM TESTTABLE")) {
        rs.next();
        assertEquals("from-slave", rs.getString(1));
    } finally {
        // 必须清掉。Tomcat 线程是复用的，不清会污染下一个请求
        DataSourceContextHolder.clear();
    }
}
```

---

## 三、知识点一：JDBC 是什么

**是什么**
Java 官方定义的一套操作数据库的接口：`Connection`（连接）、`Statement`（执行器）、`ResultSet`（结果集）。MySQL、Oracle、H2 各自写驱动去实现这套接口。

**为什么存在**
如果 Java 为每个数据库单独写一套 API，换数据库就等于重写业务代码。所以 Java 只定标准，厂商适配。

**怎么运作**
`DriverManager` 根据 URL 前缀（`jdbc:mysql:` / `jdbc:h2:`）找到对应驱动类，由它建立连接。

**对应我的代码**
`conn.createStatement()`、`st.execute(...)`、`st.executeQuery(...)` 全是 JDBC 接口，没有一行是 H2 特有的。以后换成 MySQL，这段代码不用改。

**关键对比**

| 方法 | 用途 | 返回值 |
|---|---|---|
| `execute(sql)` | CREATE / INSERT / UPDATE / DELETE | boolean，基本用不到 |
| `executeQuery(sql)` | 只用于 SELECT | `ResultSet` |
| `executeUpdate(sql)` | INSERT / UPDATE / DELETE | 影响行数 int |

---

## 四、知识点二：JDBC URL 拆解

```
jdbc:h2:mem:master;MODE=MySQL;DB_CLOSE_DELAY=-1
```

| 部分 | 含义 |
|---|---|
| `jdbc:` | 固定协议头 |
| `h2:` | 子协议，指定用 H2 驱动 |
| `mem:master` | 内存模式，库名叫 master。`mem:slave` 是另一个完全独立的库 |
| `MODE=MySQL` | 让 H2 按 MySQL 语法规则解析，减少换库时的差异 |
| `DB_CLOSE_DELAY=-1` | 最后一个连接关闭后**不要销毁**这个库 |

---

## 五、知识点三：H2 内存数据库

**是什么**
纯 Java 写的数据库，整个库可以只存在于 JVM 内存中，JVM 一停就没了。

**为什么测试用它**
不用装 MySQL、不用建账号、不用清数据。启动毫秒级，跑完自动销毁，下一个测试干干净净。pom 里 `scope=test`，不会打进最终 jar。

**`DB_CLOSE_DELAY=-1` 为什么必须加**

H2 的默认规则：**最后一个连接关闭时，内存库就销毁**。

`prepareData()` 建完表就把连接关了 → 库被销毁 → 后面 `getConnection()` 再去查，报 `Table "TESTTABLE" not found`。

`-1` 的意思是"别自动销毁，等我 JVM 退出"。

**和生产 MySQL 的对照**

| | H2 内存 | MySQL |
|---|---|---|
| 位置 | 住在 JVM 里 | 独立进程 |
| 数据 | 内存，随时蒸发 | 落盘，持久 |
| 连接 | 不是 TCP，是方法调用 | TCP 连接 |
| 对 JDBC 来说 | 长得一模一样 | 长得一模一样 |

最后一行是关键：**接口隔离**带来的好处。

---

## 六、知识点四：DataSource 不是数据库

这是最容易理解错的一个。

**是什么**
`javax.sql.DataSource` 是个接口，核心方法只有一个：`getConnection()`。它是**连接工厂**，不是数据库本身。

**为什么需要它，而不是直接用 DriverManager**

1. **解耦**：业务代码只写"给我一个连接"，不需要知道连的是哪个库、账号密码是什么。
2. **它是连接池的入口**：HikariCP 也实现 `DataSource`，它的 `getConnection()` 是"从池子里借一个"，用完还回去，而不是真新建 TCP 连接。

**我的代码里用的 `DriverManagerDataSource`**

Spring 提供的**最笨的实现**：每次 `getConnection()` 都真的 `new` 一个物理连接，没有池、没有复用。
所以它绝对不能用在生产，只适合"造个假库做测试"。

**一句话记住**
`DataSource` 是接口。实现它的可以是"一个直连"，可以是"一个连接池"，也可以是"一群数据源的调度台"——最后这个就是 `AbstractRoutingDataSource`。

这也解释了为什么路由数据源能伪装成普通数据源：**因为它本身就实现了 DataSource 接口**。

---

## 七、知识点五：try-with-resources

```java
try (Connection conn = ...; Statement st = ...) {
    ...
}
```

**这个 `try` 不是 `try-catch` 的那个 `try`。**

| | `try-catch` | `try-with-resources` |
|---|---|---|
| 职责 | 捕获异常 | 自动关闭资源 |
| 括号里 | 无 | 必须放实现 `AutoCloseable` 的对象 |
| 异常 | 被 catch 吃掉 | **照样往外抛** |

**规则**
- 括号里的对象必须实现 `AutoCloseable`（Connection / Statement / ResultSet 都实现了）
- 代码块执行完**自动调 `close()`**，抛异常退出也会调
- 多个资源按**创建顺序的倒序**关闭
- 异常不被捕获，所以方法签名要写 `throws SQLException`，否则编译不过

**为什么不用 finally**
等价于在 finally 里逐个 close，但短得多，而且不会忘。我原来那段代码 `getConnection()` 之后从不 close，跑几次就把连接耗光了。

---

## 八、知识点六：AbstractRoutingDataSource

### 是什么

Spring 内置的一个 `DataSource` 实现类。内部持有：

- `Map<Object, DataSource> targetDataSources` —— 映射表
- `DataSource defaultTargetDataSource` —— 兜底

### 为什么存在

读写分离（写走主库、读走从库）、多租户（一客户一库）、冷热分离。业务代码里绝对不想写这种判断，希望"代码不动，库自动换"。

### 怎么运作：模板方法模式

父类 `getConnection()` 把流程**写死了**，只在中间留一个空位给子类填：

```
父类 getConnection():
  ① key = determineCurrentLookupKey()          ← 空位，留给子类
  ② ds  = (key == null) ? 默认数据源
                       : targetDataSources.get(key)
  ③ return ds.getConnection()
```

所以我的 `RoutingDataSource` 只要 10 行——流程不归我管，只有"这次用哪个 key"归我管。

这就是设计模式里的**模板方法**：父类定骨架，子类填空位。

### 三个方法各管什么

| 调用 | 作用 | 注意 |
|---|---|---|
| `setTargetDataSources(Map)` | 塞映射表 | key 类型是 `Object`，字符串、枚举都行 |
| `setDefaultTargetDataSource(DataSource)` | 兜底数据源 | **参数是 DataSource 对象，不是 key 字符串** |
| `afterPropertiesSet()` | 把 Map 加工成内部可用结构 | **Spring 容器里自动调；手动 new 的必须自己调** |

### `afterPropertiesSet()` 的来历

它来自 `InitializingBean` 接口。Spring 的规矩：**Bean 的属性都注入完之后，如果实现了 `InitializingBean`，就自动调它的 `afterPropertiesSet()`**。

这就是它名字的意思——"属性设好之后"。

---

## 九、知识点七：断言与 @Test

**`assertEquals(expected, actual)`**
第一个参数是**期望值**，第二个是**实际值**。顺序写反，报错信息会非常误导人。

作用：不相等等就抛 `AssertionFailedError`，测试标红。没有它就只能靠 `System.out.println` 肉眼看，那不叫测试。

**`@Test`**
JUnit 靠扫描这个注解找测试方法；IDEA 也靠它决定要不要画绿色运行三角。

**曾经踩的坑**：方法写了、逻辑对了，但没有 `@Test`，于是 JUnit 报 `Tests run: 0`，IDEA 也不给绿箭头。

---

## 十一、一次 getConnection 的完整旅程

调 `routingDataSource.getConnection()` 时：

1. 进父类 `AbstractRoutingDataSource.getConnection()`
2. 父类调我写的 `determineCurrentLookupKey()` → 它返回 `DataSourceContextHolder.get()`
3. **场景一**：ThreadLocal 里是 `null` → 返回 `null` → 父类走默认数据源（DataSource A）→ 查到 `from-master`
4. **场景二**：刚 `set("slave")` → 返回 `"slave"` → 父类拿它去 Map 里 `get` → 拿到 DataSource B → 查到 `from-slave`
5. 父类调那个真实 DataSource 的 `getConnection()`，把连接返给我的 try-with-resources
6. 代码块结束连接自动关闭；`finally` 里 `DataSourceContextHolder.clear()`

**第 6 步为什么必须 clear**

Tomcat 的线程是复用的（线程池）。如果这条线程处理完请求没清 ThreadLocal：

- 下一个请求进来，会继承上一个请求残留的 `"slave"`
- 那条本该读主库的**写请求**，就打到从库上去了

这是动态数据源最高频的生产事故。`remove()` 不是可选项，是必须的。

**顺带说清 ThreadLocal 的边界**

ThreadLocal 不是线程池，它是"给每个线程单独配一个储物柜"。线程池是"一堆线程反复被借出去用"。两者结合才出问题：柜子不清，下一个用这条线程的人会拿到上一个人的东西。

---

## 十二、踩过的坑清单

| # | 坑 | 症状 | 正确做法 |
|---|---|---|---|
| 1 | 测试方法没写 `@Test` | `Tests run: 0`，IDEA 没有绿箭头，`assertEquals` 报红 | 加 `@Test` |
| 2 | `createDs("db")` 写死字符串 | 建表建到第三个库，查询报表不存在 | 用循环变量 `createDs(db)` |
| 3 | `setDefaultTargetDataSource("master")` | 传了字符串，`afterPropertiesSet()` 抛类型转换异常 | 传 `DataSource` 对象 |
| 4 | 连接用完不关 | 连接耗尽 | try-with-resources |
| 5 | `driverClassName` 拼成 `driveClassName` | 静默失效，配置读不到，不报错 | 字段名必须和 yml 完全一致 |
| 6 | 内部类 `DataSourceProperty` 不加 `static` | Spring 反射无参构造失败 | 嵌套配置类必须 `static` |
| 7 | `@DS("slove")` | 拼写错误，找不到数据源 | `slave` |
| 8 | 用 `ThreadLocal` 后不 `clear()` | 线程复用导致串库 | `finally` 里 `remove()` |
| 9 | `localRepository` 和 IDEA 用的 Maven 不一致 | 找不到 jar，绿箭头消失 | 三处配置要统一 |

**排查心得**：遇到"IDEA 不认测试"，先看方法上有没有 `@Test`，再去看 Maven 配置。Maven 配置是"全局食物"，`@Test` 是"这道菜本身有没有下锅"。

---

## 十三、学习资源

按这个顺序补，别跳：

| 顺序 | 主题 | 搜索关键词 | 看到什么程度 |
|---|---|---|---|
| 1 | JDBC 基础 | `JDBC 入门` | 看懂 Connection / Statement / ResultSet 三件套 |
| 2 | 连接池 | `HikariCP 原理 连接池` | 理解"为什么借还比新建快"，不用看源码 |
| 3 | 模板方法模式 | `模板方法模式 设计模式` | 会画"父类定流程、子类填空位"那张图 |
| 4 | 动态数据源 | `Spring 动态数据源 读写分离 AbstractRoutingDataSource` | 看别人怎么配 `@DS` + AOP |
| 5 | H2（可选） | `h2database.com/html/features.html` | 只查 `DB_CLOSE_DELAY` 和 `MODE` |

**不要看的**：Spring 源码解析。现在看只会加重挫败感。

---

## 十四、下一步（已过时，见 Day 8 / Day 9 章节）

> 这一段写于 Day 7 结束时，里面提到的自动配置和 DSAspect 都已完成，保留作为进度对照。
> 最新的下一步见文末「十七、Day 10 计划」。

**Day 8：`DynamicDataSourceAutoConfiguration`**

现在测试里这一坨是手动 new 的：

```java
RoutingDataSource routingDataSource = new RoutingDataSource();
routingDataSource.setTargetDataSources(targets);
routingDataSource.setDefaultTargetDataSource(...);
routingDataSource.afterPropertiesSet();
```

目标是让 Spring 自动干这些活：我在 yml 里写了配置，Starter 一启动就把 `RoutingDataSource` 装配好，使用者什么都不用写。

这一步会亲手回答一个问题：**为什么需要"自动配置"？**

然后才是 `AutoConfiguration.imports`（让 Spring Boot 发现它）→ `DSAspect`（让 `@DS` 生效）。

**目标：12 月中旬代码上 GitHub。**

---

## 十六、Day 9：AOP 切面（2026-11-12）

> 今天的主线：**让 `@DS("slave")` 真正生效**。昨天做完了"配置能自动装配"，今天补上"注解能切库"。

### Day 9 交付物

| 文件 | 说明 |
|---|---|
| `aop/DSAspect.java` | 切面，拦截 `@DS` 方法 |
| `config/DynamicDataSourceAutoConfiguration.java` | 增加 `dsAspect()` 注册 Bean |
| `test/.../DSAspectTest.java` | 注解切库验证 |

**测试**：`Tests run: 3, Failures: 0, BUILD SUCCESS`

### 1. AOP 到底是什么

**是什么**
不改原方法的前提下增加功能的机制。Spring 发现某个 Bean 的方法「匹配切点」，就不返回原始对象，改给一个**代理对象**。调用方以为在调原方法，实际先经过代理，代理插入切面逻辑后再调原方法。

**为什么需要**
切库和业务逻辑完全无关。没有 AOP，`@DS` 就是个废注解，你得每个方法手写：

```java
DataSourceContextHolder.set("slave");
try { ...业务... } finally { DataSourceContextHolder.clear(); }
```

十个方法写十遍，漏一个就是生产事故。

**怎么运作**

```
① Spring 创建 DemoService 这个 Bean
② 发现方法上有 @DS，匹配切点 @annotation(ds)
③ 不返回原始对象，返回 CGLIB 生成的子类（代理）
④ 调 demoService.query() → 进代理 → 切面代码 → 调原方法
```

**四个零件的分工**

| 零件 | 角色 |
|---|---|
| `@DS` | 标记（我想走哪个库） |
| `DSAspect` | 执行者（读标记、设 ThreadLocal） |
| `DataSourceContextHolder` | 信箱（当前线程的储物柜） |
| `RoutingDataSource` | 最终读信箱派活的人 |

### 2. 为什么必须 @Around，不能用 @After（面试常问）

| 通知 | 能用 | 原因 |
|---|---|---|
| `@Before` | ❌ | 只能 set，管不到清理 |
| `@After` | ❌ | **方法抛异常时不执行**，ThreadLocal 清不掉 |
| `@AfterReturning` | ❌ | 只在正常返回时执行 |
| `@Around` | ✅ | 能包住整个方法，try-finally 保证 100% 清理 |

这个 finally 是动态数据源的命门。一旦业务抛异常，残留的 key 会污染这条线程接下来的所有请求。

### 3. 切点表达式：三种写法，只有一个能用

```java
@Around("@annotation(ds)")                          // ✅ 参数名引用，能拿到注解实例
@Around("@annotation('ds')")                        // ❌ 加引号 → 当成类型名找注解 → 永不匹配
@Around("@annotation(com.minids.annotation.DS)")    // ❌ 写全类名 → 只匹配，参数绑不上
```

**今天踩的坑就是第二种**：`@annotation('ds')` 加了单引号，AspectJ 把它当字符串/类型名解析，找不到叫 `ds` 的注解类型，切点永远不匹配，切面静默失效。

**规则**：要读 `ds.value()`，就必须用**参数名引用**，且名字必须和下面那个 `DS ds` 参数一模一样。

### 4. 三个必须记住的细节

**`ProceedingJoinPoint` 必须放第一个参数**
这是 Spring AOP 的约定，它代表被拦截的方法，`proceed()` 就是去执行原方法。

**`throws Throwable` 而不是 `Exception`**
`proceed()` 的签名就是 `throws Throwable`，原方法可能抛 `Error`。

**切面必须注册成 Bean**
`@Aspect` 只是标记。类不进容器，AOP 机制扫不到，运行时不生效也不报错。

### 5. 切面不生效怎么排查（方法论）

切点写错**不会报错**，只会悄悄不匹配。按这个顺序查：

1. 切面是不是 Bean（容器里有没有）
2. AOP 开了没（Boot 项目自动开；测试里必须手写 `@EnableAspectJAutoProxy`）
3. 切点表达式对不对（引号、名字、括号）
4. **在切面第一行打断点或加日志**

第 4 条是分水岭：
- 没进切面 → 切点/配置问题
- 进了切面但结果不对 → 逻辑问题

### 6. `@EnableAspectJAutoProxy(proxyTargetClass = true)` 为什么必须写

- `@SpringJUnitConfig` 不加载 Boot 自动配置，所以 `AopAutoConfiguration` 不生效，AOP 默认没开
- `proxyTargetClass = true`：`DemoService` 没有实现接口，必须用 **CGLIB 生成子类**代理。默认值 false 走 JDK 动态代理（要求目标类有接口），不改会失败
- Spring Boot 生产环境默认就是 true（`spring.aop.proxy-target-class` 默认开启）

### 7. 三层测试的分工

| 测试类 | 层次 | 测什么 |
|---|---|---|
| `RoutingDataSourceTest` | **零件层** | 路由逻辑本身（手动 new + 手动 set） |
| `AutoConfigurationTest` | **装配层** | 自动配置把 Bean 装出来（不碰注解） |
| `DSAspectTest` | **使用层** | 用户视角，标个注解就切库 |

这三层加起来，能讲成一句完整的话：**零件好使、组装好使、用起来好使**。

### 8. Day 9 踩坑记录

| 坑 | 原因 | 修法 |
|---|---|---|
| `@annotation('ds')` 加了单引号 | 被当成类型名字符串，切点永不匹配，切面静默失效 | 去掉引号改为 `@annotation(ds)` |
| `import javax.sql.DataSource` 未使用 | 从别处复制残留 | 删除 |

### 9. Day 9 完成后的一句话总结

> `@DS` 注解由 `DSAspect` 的 `@Around` 环绕通知拦截，执行前把注解值写入 `ThreadLocal`，`AbstractRoutingDataSource` 在 `getConnection()` 时读取该值决定走哪个库，方法结束由 `finally` 保证清理。三个测试分别覆盖路由逻辑、自动装配、注解生效三层。

### 10. 面试怎么讲这个项目（四句话故事）

1. **发现**：以前项目里切库是手写 `static Connection`，多线程互相覆盖
2. **设计**：用 ThreadLocal 隔离线程，用注解声明意图
3. **实现**：`@DS` + AOP 切面 + `AbstractRoutingDataSource` 模板方法
4. **验证**：三层测试覆盖，从零件到整体

---

## 十七、Day 10：类级注解与切点表达式（2026-11-17）

> 今天的主线：**切点表达式这门小语言**。补完类级 `@DS` 的同时，把 AspectJ 表达式的规则一次搞清。

### Day 10 交付物

| 文件 | 改动 |
|---|---|
| `aop/DSAspect.java` | 切点扩展为 `@annotation(全类名) \|\| @within(全类名)`；注解取值改为反射（方法级 > 类级）；加 null 兜底 |
| `test/.../DSAspectTest.java` | 新增 `ClassLevelService` 靶子 + `shouldSwitchByClassAnnotation()` 测试 |

测试从 3 个变 4 个，全绿：

```
Tests run: 4, Failures: 0, Errors: 0
```

### 1. 切点表达式是什么

`@Around("...")` 括号里那一串，是 **AspectJ 切点表达式（pointcut expression）**——一门独立的小语言，可以理解成"筛选方法的 WHERE 子句"。Spring 启动时拿它把容器里所有 Bean 的方法过一遍，符合条件的就套一层代理。

它由三个部件拼成：

| 部件 | 长什么样 | 作用 |
|---|---|---|
| **匹配器** | `execution(...)`、`@annotation(...)`、`@within(...)`、`within(...)`、`args(...)`、`bean(...)` | 按什么标准筛 |
| **通配符** | `*`（任意）、`..`（任意层级/参数） | 只在 `execution` / `within` 里用 |
| **逻辑运算符** | `&&` 交集、`\|\|` 并集、`!` 取反 | 连接多个匹配器 |

注意：Spring AOP 借用的是 AspectJ 的**语法**，但只实现了"方法执行"这一类连接点（构造器、字段访问都不支持）。

### 2. 常用匹配器一览

| 匹配器 | 判断依据 | 举例 |
|---|---|---|
| `execution(* com.minids..*Service.*(..))` | 方法签名（返回值/包/类/方法名/参数） | 按命名规范批量拦 |
| `within(com.minids.service..*)` | 方法所在类/包的名字 | 按包圈范围 |
| `@within(注解)` | **类上**的注解 | 类级开关 |
| `@annotation(注解)` | **方法上**的注解 | 方法级开关 |
| `@target(注解)` | **运行时对象**的类上有无注解 | 与 @within 极像，见第 6 节 |
| `args(java.lang.String, ..)` | 方法参数类型 | 按参数筛 |
| `bean(userService)` | Bean 名字 | 只拦某个 Bean |

### 3. @annotation 与 @within 的区别

| | `@annotation(...)` | `@within(...)` |
|---|---|---|
| 检查对象 | 方法本身有无注解 | 方法**声明所在的类**有无注解 |
| 命中范围 | 只有被标注的少数方法 | 类上标了，类里所有可代理方法全命中 |
| 项目里用途 | 单个方法特殊处理 | Service 类整体走某个库 |

两个用 `||` 连起来 = 方法级、类级都支持，这就是今天的改动。

### 4. 括号里能写什么：三选一，只有一个对

| 写法 | 结果 | 说明 |
|---|---|---|
| `@annotation(com.minids.annotation.DS)` | ✅ **本项目采用** | 全限定类型名，只判断"切不切"，不绑定 |
| `@annotation(ds)` | ⚠️ 能跑但有坑 | `ds` 是**切面方法的参数名**，表示"把注解实例绑定给这个参数" |
| `@annotation('ds')` | ❌ 永不匹配 | 加了引号被当成字符串，静默失效（Day 9 踩过） |
| `@annotation(DS.class)` | ❌ **启动报错** | `.class` 是 Java 语法，切点表达式是纯文本，不参与编译。报 `Pointcut is not well-formed` |

**为什么 `DS.class` 会搞混**：切面代码里有一行 `method.getAnnotation(DS.class)`，那里 `.class` 是对的。区别在于两个世界：

| | 切点表达式（字符串） | 反射 API（Java 代码） |
|---|---|---|
| 要什么 | 类型**名字** | `Class` **对象** |
| 写法 | `@annotation(com.minids.annotation.DS)` | `getAnnotation(DS.class)` |
| 谁解析 | AspectJ 运行期读字符串 | javac 编译期 |

一句话：**字符串里给名字，代码里给 `.class`。**

### 5. 为什么不用 `@within(ds) || @annotation(ds)`

这是原计划想用的写法，实际不可行，原因是**变量绑定（binding）在 `||` 上会产生歧义**：

- 类上标 `@DS("master")`、方法上标 `@DS("slave")` → 两个分支都命中 → 绑哪个实例？规范没定义
- 类上有、方法上没有 → `@within(ds)` 命中但 `@annotation(ds)` 分支里 `ds` **没被绑定**

AspectJ 要求一个变量在所有分支路径上都被绑定，否则叫 **inconsistent binding**。实际表现：

- 多数情况 Spring **启动时抛异常**，报错含 `inconsistent binding of formal unbound in pointcut`
- 少数情况放过解析，运行时 `ds` 为 `null`，`ds.value()` 直接 NPE

**本项目的解法：切点只筛、不绑；取值交给反射。**

```java
@Around("@annotation(com.minids.annotation.DS) || @within(com.minids.annotation.DS)")
public Object switchDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();

    DS ds = method.getAnnotation(DS.class);              // 先找方法
    if (ds == null) {
        ds = method.getDeclaringClass().getAnnotation(DS.class);   // 再找类
    }
    ...
}
```

本质是**把"匹配"和"取值"拆开**：切点负责筛（框架擅长），取哪个值由 Java 代码决定（完全可控），优先级变成显式的 `if`，方法级覆盖类级，读代码的人一眼就懂。

反射这三行涉及四个 API：

| API | 作用 | 在哪见过 |
|---|---|---|
| `joinPoint.getSignature()` | 拿方法签名 | Day 9 就写了，只是没解包 |
| `MethodSignature` | AspectJ 对签名的封装，能拿出 `Method` | 新 |
| `method.getAnnotation(DS.class)` | 查方法上的注解，没有返回 null | Day 2 反射 demo |
| `method.getDeclaringClass()` | 这个方法定义在哪个类上，返回 `Class<?>` | 新 |

### 6. @within 的三个坑

**坑 1：子类不自动继承**

`@DS` 没加 `@Inherited`，所以：

```java
@DS("slave")
class BaseService { }
class OrderService extends BaseService { }   // ← 不会被切，走默认主库
```

要用继承效果，给 `DS` 加 `@Inherited`，或者在子类上再标一次。

**坑 2：内部调用照样失效**

```java
@DS("slave")
public class OrderService {
    public void a() { this.b(); }   // this.b() 不经过代理
    public void b() { ... }
}
```

类级注解**不会**让人逃开这条规则，别以为标在类上就"全类安全"。

**坑 3：private / final 方法切不到**

CGLIB 靠继承重写方法，`private` / `final` 无法重写，直接跳过。

### 7. @within 与 @target 的区别（面试爱问）

| | `@within(注解)` | `@target(注解)` |
|---|---|---|
| 看哪个类 | **声明**这个方法的类（静态） | **运行时**对象所属的类（动态） |
| 语义 | 我这段代码写在哪个类里 | 实际跑起来是哪个对象 |
| 选择 | 90% 场景够用 | 需要看运行时实现类的少数场景，性能略差 |

### 8. 命名切点 @Pointcut（多个通知复用同一个切点）

```java
@Pointcut("@annotation(com.minids.annotation.DS) || @within(com.minids.annotation.DS)")
public void dsPointcut() {}      // 方法体必须为空，它从不被调用，只是个名字

@Around("dsPointcut()")
public Object switchDataSource(ProceedingJoinPoint joinPoint) throws Throwable { ... }
```

### 9. Day 10 踩坑与修正记录

| 项 | 问题 | 修法 |
|---|---|---|
| 类注释 | 还写着旧的 `@annotation(ds)`，并把全类名写法标成 ❌ | 重写注解说明，讲清"只筛不绑"的理由 |
| `String value = ds.value()` | `ds` 为 null 时 NPE | 前面加 `if (ds == null) return joinPoint.proceed();` |
| `import org.aspectj.lang.Signature` | 未使用（直接强转 MethodSignature） | 删除 |
| 测试覆盖 | 只测了方法级，`@within` 分支没被验证过 | 新增 `ClassLevelService`（类标 slave，方法 `@DS("master")` 覆盖），加测试 `shouldSwitchByClassAnnotation()` |

### 10. Day 10 完成后的一句话总结（面试话术）

> 切点是 `@annotation(DS) || @within(DS)`，覆盖方法级和类级两种用法。我**没有用变量绑定**（`@annotation(ds)` 那种），因为变量在 `||` 两个分支上绑定会产生歧义，Spring 会报 inconsistent binding。所以切点只负责筛选，进切面后用 `MethodSignature` 拿到 `Method`，先查方法上的注解、没有再查类上的，优先级方法级高于类级。

背后四个知识点：**匹配器语义、绑定机制、`||` 的歧义、反射取值**。

---

## 十八、下一步

代码层面已完结（4 个测试全绿），剩下三件收尾的事：

| # | 事项 | 说明 |
|---|---|---|
| 1 | `.gitignore` 补 `.idea/` | 现在 `git status` 里还有 `deployment.xml`、`encodings.xml`、`inspectionProfiles/` 三个漏网 |
| 2 | `README.md` | GitHub 门面：一句话定位 + yml 配置示例 + `@DS` 用法示例 + 测试说明 |
| 3 | 推 GitHub | 原计划 12 月中，实际 11 月中就能完成 |

**代码写完之后真正的活**：把这些 NOTES 变成能脱稿讲出来的东西。计划每周复述一个模块（自动配置 / AOP / ThreadLocal / 路由），按面试官视角接受追问。
