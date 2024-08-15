/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.core.datasource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.commons.lang3.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Basic data source, used to implement several low-frequency usage methods in the data source
 * interface 基础数据源，用于实现数据源接口中几种低频的使用方法
 *
 * @author yh263208
 * @date 2021-11-09 15:59
 * @since ODC_release_3.2.2
 * @see javax.sql.DataSource
 */
@Slf4j
public abstract class BaseDriverBasedDataSource implements DataSource {
    /** Logger available to subclasses. */
    protected final Log logger = LogFactory.getLog(getClass());
    @Getter
    @Setter
    private String url;
    @Getter
    @Setter
    private String username;
    @Getter
    @Setter
    private String password;
    @Getter
    @Setter
    private String catalog;
    @Getter
    @Setter
    private String schema;
    @Getter
    @Setter
    private Properties connectionProperties;

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setLoginTimeout(int timeout) throws SQLException {
        throw new UnsupportedOperationException("setLoginTimeout");
    }

    /**
     * LogWriter methods are not supported.
     */
    @Override
    public PrintWriter getLogWriter() {
        throw new UnsupportedOperationException("getLogWriter");
    }

    /**
     * LogWriter methods are not supported.
     */
    @Override
    public void setLogWriter(PrintWriter pw) throws SQLException {
        throw new UnsupportedOperationException("setLogWriter");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return (T) this;
        }
        throw new SQLException("DataSource of type [" + getClass().getName() +
                "] cannot be unwrapped as [" + iface.getName() + "]");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

    /**
     * Build properties for the Driver, including the given username and password (if any), and obtain a
     * corresponding Connection. 为驱动构建属性，包括给定的用户名和密码（如果有），并获取相应的连接
     *
     * @param username the name of the user
     * @param password the password to use
     * @return the obtained Connection
     * @throws SQLException in case of failure
     */
    protected Connection newConnectionFromDriver(String username, String password)
            throws ClassNotFoundException, SQLException {
        // 创建一个属性集合，用于存储合并后的属性
        Properties mergedProps = new Properties();
        // 获取连接属性
        Properties connProps = getConnectionProperties();
        // 如果连接属性不为空，则将其添加到合并属性集合中
        if (connProps != null) {
            mergedProps.putAll(connProps);
        }
        // 如果用户名不为空，则将其添加到合并属性集合中
        if (username != null) {
            mergedProps.setProperty("user", username);
        }
        // 如果密码不为空，则将其添加到合并属性集合中
        if (password != null) {
            mergedProps.setProperty("password", password);
        }
        // 通过合并属性集合获取连接
        Connection con = newConnectionFromDriver(mergedProps);
        try {
            // 调用连接创建事件监听器的回调方法
            onConnectionCreated(con);
        } catch (Throwable throwable) {
            // 如果回调方法执行失败，则记录警告日志
            log.warn("The connection creation event listener callback method failed to execute", throwable);
        }
        // 如果目录不为空，则设置连接的目录
        if (this.catalog != null) {
            con.setCatalog(this.catalog);
        }
        // 如果模式不为空，则设置连接的模式
        if (this.schema != null) {
            con.setSchema(this.schema);
        }
        // 初始化连接
        initConnection(con);
        // 返回连接
        return con;
    }

    /**
     * New a {@code Connection} instance
     *
     * @param connProperties {@code Properties}
     * @return {@code Connection}
     */
    private Connection newConnectionFromDriver(Properties connProperties) throws ClassNotFoundException, SQLException {
        String url = getUrl();
        Validate.notNull(url, "URL can not be null");
        Driver driver = getDriver();
        if (driver != null) {
            return driver.connect(url, connProperties);
        }
        return DriverManager.getConnection(url, connProperties);
    }

    /**
     * Get a {@code Driver}
     *
     * @return {@code Driver}
     */
    abstract protected Driver getDriver() throws ClassNotFoundException;

    /**
     * Init {@code Connection}, init some variables
     *
     * @param connection {@code Connection} to be inited
     */
    abstract protected void initConnection(Connection connection) throws SQLException;

    /**
     * Callback function when the connection is created
     *
     * @param connection {@link Connection}
     */
    abstract protected void onConnectionCreated(Connection connection);

}
