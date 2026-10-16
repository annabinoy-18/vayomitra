package com.vayomitra.service;

import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;
import com.vayomitra.security.Passwords;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class SeedService {
    private final Database database;
    private final AppConfig config;

    public SeedService(Database database, AppConfig config) {
        this.database = database;
        this.config = config;
    }

    public void seedIfEmpty() throws SQLException {
        if (!config.seedDemoData()) {
            return;
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }
        String passwordHash = Passwords.hash(config.demoPassword(), config.bcryptCost());
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                UUID adminId = insertUser(connection, "admin@vayomitra.test", passwordHash, "ADMIN");
                UUID elderUser = insertUser(connection, "elder@vayomitra.test", passwordHash, "ELDER");
                UUID volunteerUser = insertUser(connection, "volunteer@vayomitra.test", passwordHash, "VOLUNTEER");
                UUID elderId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO elders (
                            id, user_id, full_name, date_of_birth, gender, phone, address, locality, city, state, country,
                            pin_code, latitude, longitude, emergency_contact_name, emergency_contact_phone
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    statement.setObject(1, elderId);
                    statement.setObject(2, elderUser);
                    statement.setString(3, "Kamala Iyer");
                    statement.setDate(4, Date.valueOf(LocalDate.now().minusYears(72)));
                    statement.setString(5, "FEMALE");
                    statement.setString(6, "9876500001");
                    statement.setString(7, "12 Temple Street");
                    statement.setString(8, "Shivajinagar");
                    statement.setString(9, "Pune");
                    statement.setString(10, "Maharashtra");
                    statement.setString(11, "India");
                    statement.setString(12, "411005");
                    statement.setDouble(13, 18.5204);
                    statement.setDouble(14, 73.8567);
                    statement.setString(15, "Arjun Iyer");
                    statement.setString(16, "9876500002");
                    statement.executeUpdate();
                }
                UUID volunteerId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO volunteers (
                            id, user_id, full_name, date_of_birth, gender, phone, address, locality, city, state, country,
                            pin_code, latitude, longitude, educational_qualification, skills, experience_years,
                            preferred_service_radius_km, offers_driving, driving_licence_number, driving_licence_expiry
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    statement.setObject(1, volunteerId);
                    statement.setObject(2, volunteerUser);
                    statement.setString(3, "Meera Joshi");
                    statement.setDate(4, Date.valueOf(LocalDate.now().minusYears(28)));
                    statement.setString(5, "FEMALE");
                    statement.setString(6, "9876500010");
                    statement.setString(7, "88 FC Road");
                    statement.setString(8, "Deccan");
                    statement.setString(9, "Pune");
                    statement.setString(10, "Maharashtra");
                    statement.setString(11, "India");
                    statement.setString(12, "411004");
                    statement.setDouble(13, 18.5314);
                    statement.setDouble(14, 73.8446);
                    statement.setString(15, "Bachelor of Science, First-aid certified");
                    statement.setString(16, "driving, grocery support, companionship, technology, booking, pet care, home maintenance");
                    statement.setInt(17, 4);
                    statement.setDouble(18, 15);
                    statement.setBoolean(19, true);
                    statement.setString(20, "MH12-DEMO-1234");
                    statement.setDate(21, Date.valueOf(LocalDate.now().plusYears(3)));
                    statement.executeUpdate();
                }
                String[] types = {
                        "TRANSPORTATION", "GROCERY_SUPPORT", "TECHNOLOGY_SUPPORT", "LONELINESS_SUPPORT",
                        "BOOKING_SUPPORT", "PET_ASSISTANCE", "MINOR_HOME_MAINTENANCE"
                };
                for (String type : types) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO volunteer_service_preferences (volunteer_id, assistance_type) VALUES (?, ?)")) {
                        statement.setObject(1, volunteerId);
                        statement.setString(2, type);
                        statement.executeUpdate();
                    }
                }
                for (int day = 1; day <= 7; day++) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO volunteer_availability (volunteer_id, weekday, start_time, end_time)
                            VALUES (?, ?, ?, ?)
                            """)) {
                        statement.setObject(1, volunteerId);
                        statement.setInt(2, day);
                        statement.setTime(3, Time.valueOf(LocalTime.of(8, 0)));
                        statement.setTime(4, Time.valueOf(LocalTime.of(20, 0)));
                        statement.executeUpdate();
                    }
                }
                seedVerifiedCertificate(connection, volunteerId, "TENTH_STANDARD", "Class 10 certificate");
                seedVerifiedCertificate(connection, volunteerId, "QUALIFICATION", "B.Sc. certificate");
                seedVerifiedCertificate(connection, volunteerId, "DRIVING_LICENCE", "Driving licence");
                connection.commit();
                System.out.println("Seeded demo accounts (password from DEMO_PASSWORD / backend/.env.example).");
                System.out.println("Admin user id " + adminId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private UUID insertUser(Connection connection, String email, String passwordHash, String role) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, role);
            statement.executeUpdate();
        }
        return id;
    }

    private void seedVerifiedCertificate(Connection connection, UUID volunteerId, String type, String title) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO volunteer_certificates (volunteer_id, certificate_type, title, verification_status)
                VALUES (?, ?, ?, 'VERIFIED')
                """)) {
            statement.setObject(1, volunteerId);
            statement.setString(2, type);
            statement.setString(3, title);
            statement.executeUpdate();
        }
    }
}
