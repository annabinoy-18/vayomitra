package com.vayomitra.db;

import com.vayomitra.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private final AppConfig config;

    public Database(AppConfig config) {
        this.config = config;
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL JDBC driver not found", e);
        }
    }

    public Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
        connection.setAutoCommit(true);
        return connection;
    }

    public AppConfig config() {
        return config;
    }
}
