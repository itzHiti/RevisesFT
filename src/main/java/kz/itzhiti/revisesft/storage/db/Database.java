package kz.itzhiti.revisesft.storage.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kz.itzhiti.revisesft.RevisesFT;
import kz.itzhiti.revisesft.storage.Config;
import lombok.Getter;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

@Getter
public class Database {
    private static final String MYSQL = "mysql";

    private static Database instance;

    private HikariDataSource dataSource;
    private final String type;

    public Database() {
        instance = this;
        this.type = Config.getConfig()
                .getString("storage.type", "h2")
                .toLowerCase();

        HikariConfig config = new HikariConfig();

        if (MYSQL.equals(type)) {
            String host = Config.getConfig().getString("storage.mysql.host", "localhost");
            int port = Config.getConfig().getInt("storage.mysql.port", 3306);
            String database = Config.getConfig().getString("storage.mysql.database", "revises");
            String user = Config.getConfig().getString("storage.mysql.user", "root");
            String password = Config.getConfig().getString("storage.mysql.password", "");
            String params = Config.getConfig().getString(
                    "storage.mysql.params",
                    "useSSL=false&characterEncoding=utf8"
            );

            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params);
            config.setUsername(user);
            config.setPassword(password);
        } else {
            // H2
            String filePath = Config.getConfig().getString(
                    "storage.h2.file",
                    "database/revises"
            );

            File dbFile = new File(RevisesFT.getInstance().getDataFolder(), filePath);
            File parent = dbFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            config.setDriverClassName("org.h2.Driver");
            config.setJdbcUrl("jdbc:h2:" + dbFile.getAbsolutePath());
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);

        dataSource = new HikariDataSource(config);
    }

    public static Database getInstance() {
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
