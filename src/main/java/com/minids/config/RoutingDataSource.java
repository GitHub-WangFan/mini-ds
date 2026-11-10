package com.minids.config;

import com.minids.context.DataSourceContextHolder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 路由数据源：继承 Spring 自带的 AbstractRoutingDataSource，
 * 唯一要做的就是在取连接时回答"当前该用哪个 key"。
 */
public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}
