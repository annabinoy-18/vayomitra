package com.vayomitra.security;

import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;
import com.vayomitra.util.Validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public final class AdminProvisioner {
    private AdminProvisioner() {
    }

    public static void main(String[] args) throws Exception {
        String email = value(System.getenv("ADMIN_EMAIL")).toLowerCase(Locale.ROOT);
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null) {
            password = "";
        }
        Validation validation = new Validation();
        validation.require("email", email, 3, 255);
        validation.email("email", email);
        validation.password("password", password);
        if (password.length() < 12) {
            validation.errors().put("password", "Administrator password must be at least 12 characters.");
        }
        validation.throwIfInvalid();

        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path projectRoot = workingDirectory.getFileName() != null
                && workingDirectory.getFileName().toString().equals("backend")
                ? workingDirectory.getParent()
                : workingDirectory;
        if (projectRoot == null || !Files.isDirectory(projectRoot.resolve("backend"))) {
            throw new IllegalStateException("Run the provisioner from the repository root or backend directory.");
        }

        AppConfig config = AppConfig.load(projectRoot);
        Database database = new Database(config);
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement exists = connection.prepareStatement(
                        "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)")) {
                    exists.setString(1, email);
                    try (ResultSet result = exists.executeQuery()) {
                        if (result.next()) {
                            throw new IllegalStateException("An account with this email already exists.");
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO users (id, email, password_hash, role, account_status)
                        VALUES (?, ?, ?, 'ADMIN', 'ACTIVE')
                        """)) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setString(2, email);
                    insert.setString(3, Passwords.hash(password, config.bcryptCost()));
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (RuntimeException | SQLException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        System.out.println("Administrator account provisioned.");
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}