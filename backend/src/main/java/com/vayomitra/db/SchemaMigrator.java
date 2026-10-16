package com.vayomitra.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

public final class SchemaMigrator {
    private final Database database;
    private final Path migrationsDir;

    public SchemaMigrator(Database database, Path migrationsDir) {
        this.database = database;
        this.migrationsDir = migrationsDir;
    }

    public void migrate() throws SQLException, IOException {
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        filename TEXT PRIMARY KEY,
                        applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
        }
        if (!Files.isDirectory(migrationsDir)) {
            throw new IllegalStateException("Migrations folder not found: " + migrationsDir);
        }
        List<Path> files = Files.list(migrationsDir)
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .sorted()
                .collect(Collectors.toList());
        for (Path file : files) {
            String filename = file.getFileName().toString();
            if (alreadyApplied(filename)) {
                continue;
            }
            String sql = Files.readString(file, StandardCharsets.UTF_8);
            try (Connection connection = database.connection()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO schema_migrations(filename) VALUES (?)")) {
                        insert.setString(1, filename);
                        insert.executeUpdate();
                    }
                    connection.commit();
                    System.out.println("Applied migration " + filename);
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    private boolean alreadyApplied(String filename) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM schema_migrations WHERE filename = ?")) {
            statement.setString(1, filename);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }
}
