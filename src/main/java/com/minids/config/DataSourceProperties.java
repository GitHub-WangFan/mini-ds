package com.minids.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多数据源配置：绑定 application.yml 中 mini.ds 前缀，默认数据源为 master。
 */
@ConfigurationProperties(prefix = "mini.ds")
public class DataSourceProperties {

    // 默认数据源key  master,未标@DS时使用
    private String primary = "master";

    //数据源清单 key=数据源 value=连接信息
    private Map<String, DataSourceProperty> datasource = new LinkedHashMap<>();

    //数据源的get/set方法
    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    //数据源清单的get/set方法
    public Map<String, DataSourceProperty> getDatasource() {
        return datasource;
    }

    public void setDatasource(Map<String, DataSourceProperty> datasource) {
        this.datasource = datasource;
    }

    /**
     * 单个数据源的连接信息 必须是 static 内部类——Spring 要用它独立创建实例。
     */

    public static class DataSourceProperty {
        private String driverClassName;
        private String url;
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }
}
