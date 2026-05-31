package kz.itzhiti.revisesft.storage.db;

import kz.itzhiti.revisesft.RevisesFT;
import lombok.Getter;
import lombok.Value;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ReviseRepository {
    private static final String CREATE_REVISES_TABLE = "CREATE TABLE IF NOT EXISTS revises (" +
            "id INT AUTO_INCREMENT PRIMARY KEY," +
            "moderator VARCHAR(36) NOT NULL," +
            "target VARCHAR(36) NOT NULL," +
            "reason VARCHAR(255) NOT NULL," +
            "rank VARCHAR(50) NOT NULL," +
            "start_time BIGINT NOT NULL," +
            "end_time BIGINT NOT NULL," +
            "room_number INT NOT NULL," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String INSERT_REVISE = "INSERT INTO revises " +
            "(moderator, target, reason, rank, start_time, end_time, room_number) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_REVISES_BY_TARGET = "SELECT moderator, target, reason, rank, start_time, end_time, room_number, created_at " +
            "FROM revises WHERE LOWER(target) = LOWER(?) ORDER BY end_time DESC LIMIT 10";

    @Getter
    private static ReviseRepository instance;

    private final Database database;

    public ReviseRepository(Database database) {
        instance = this;
        this.database = database;
        createTables();
    }

    private void createTables() {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_REVISES_TABLE)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            RevisesFT.getInstance().getLogger().severe("Ошибка создания таблиц: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveRevise(String moderator, String target, String reason, String rank,
                          long startTime, long endTime, int roomNumber) {
        Bukkit.getScheduler().runTaskAsynchronously(RevisesFT.getInstance(), () -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_REVISE)) {

                stmt.setString(1, moderator);
                stmt.setString(2, target);
                stmt.setString(3, reason);
                stmt.setString(4, rank);
                stmt.setLong(5, startTime);
                stmt.setLong(6, endTime);
                stmt.setInt(7, roomNumber);

                stmt.executeUpdate();

            } catch (SQLException e) {
                RevisesFT.getInstance().getLogger().severe("Ошибка сохранения проверки: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void findByTarget(String targetName, Consumer<List<ReviseLog>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(RevisesFT.getInstance(), () -> {
            List<ReviseLog> logs = loadByTarget(targetName);
            Bukkit.getScheduler().runTask(RevisesFT.getInstance(), () -> callback.accept(logs));
        });
    }

    private List<ReviseLog> loadByTarget(String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return Collections.emptyList();
        }

        List<ReviseLog> logs = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_REVISES_BY_TARGET)) {

            stmt.setString(1, targetName);

            try (ResultSet resultSet = stmt.executeQuery()) {
                while (resultSet.next()) {
                    logs.add(readLog(resultSet));
                }
            }
        } catch (SQLException e) {
            RevisesFT.getInstance().getLogger().severe("Ошибка загрузки логов проверок: " + e.getMessage());
            e.printStackTrace();
        }

        return logs;
    }

    private ReviseLog readLog(ResultSet resultSet) throws SQLException {
        long endTime = resultSet.getLong("end_time");
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        long createdAtMillis = createdAt != null ? createdAt.getTime() : endTime;

        return new ReviseLog(
                resultSet.getString("moderator"),
                resultSet.getString("target"),
                resultSet.getString("reason"),
                resultSet.getString("rank"),
                resultSet.getLong("start_time"),
                endTime,
                resultSet.getInt("room_number"),
                createdAtMillis
        );
    }

    @Value
    public static class ReviseLog {
        String moderator;
        String target;
        String reason;
        String rank;
        long startTime;
        long endTime;
        int roomNumber;
        long createdAt;
    }
}

