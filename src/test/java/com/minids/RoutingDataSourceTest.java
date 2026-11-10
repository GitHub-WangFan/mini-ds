package com.minids;

import com.minids.config.RoutingDataSource;
import com.minids.context.DataSourceContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class RoutingDataSourceTest {

    /**
     * 造一个指向 H2 内存库的 DataSource。
     * DB_CLOSE_DELAY=-1 表示：连接全关了也别销毁这个库，否则第二个连接建表时库已经没了
     */
    private DataSource createDs(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    /**
     * 给指定的库建表 + 插一条数据，数据内容用来区分是哪个库
     */
    private void prepareData(String dbName, String value) throws SQLException {
        // try-with-resources：代码块结束时自动 close，不用手写 finally
        try (Connection conn = createDs(dbName).getConnection();
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE TESTTABLE (test varchar(50))");
            st.execute("INSERT INTO TESTTABLE VALUES ('" + value + "')");
        }
    }

    /**
     * 核心断言：上下文里没有 key 时走默认库；设了 key 时走对应的库
     */
    @Test
    void routeToCorrectDataSource() throws SQLException {
        // 1. 两个库各准备一条不同内容的数据
        prepareData("master", "from-master");
        prepareData("slave", "from-slave");

        // 2. 组装路由数据源
        RoutingDataSource routingDataSource = new RoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put("master", createDs("master"));
        targets.put("slave", createDs("slave"));
        routingDataSource.setTargetDataSources(targets);
        // 注意：这里要传 DataSource 对象，不是 "master" 这个字符串 key
        routingDataSource.setDefaultTargetDataSource(createDs("master"));
        // 手动触发一次，Spring 里是由容器调用的
        routingDataSource.afterPropertiesSet();

        // 场景一：ThreadLocal 里没东西 → determineCurrentLookupKey() 返回 null → 走默认库
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
            // 不管成不成功都要清掉，否则这条线程后续的请求会一直被带到 slave
            DataSourceContextHolder.clear();
        }
    }
}
