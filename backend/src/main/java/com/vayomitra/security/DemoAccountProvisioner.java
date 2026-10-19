package com.vayomitra.security;

import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DemoAccountProvisioner {
    private static final List<String> ASSISTANCE_TYPES = List.of(
            "TRANSPORTATION", "GROCERY_SUPPORT", "TECHNOLOGY_SUPPORT", "LONELINESS_SUPPORT",
            "BOOKING_SUPPORT", "HOME_NURSE_ASSISTANCE", "PET_ASSISTANCE", "MINOR_HOME_MAINTENANCE",
            "EMERGENCY_ASSISTANCE"
    );
    private static final SecureRandom RANDOM = new SecureRandom();

    private DemoAccountProvisioner() {
    }

    public static void main(String[] args) throws Exception {
        int countPerRole = args.length == 0 ? 10 : Integer.parseInt(args[0]);
        if (countPerRole < 1 || countPerRole > 50) {
            throw new IllegalArgumentException("Account count per role must be from 1 to 50.");
        }
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
        List<DemoAccount> accounts = new ArrayList<>(countPerRole * 2);
        for (int i = 1; i <= countPerRole; i++) {
            accounts.add(new DemoAccount("ELDER", email("elder", i), randomPassword(), i));
            accounts.add(new DemoAccount("VOLUNTEER", email("volunteer", i), randomPassword(), i));
        }

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                ensureEmailsAvailable(connection, accounts);
                for (DemoAccount account : accounts) {
                    UUID userId = insertUser(connection, account, config.bcryptCost());
                    if ("ELDER".equals(account.role)) {
                        insertElder(connection, userId, account.index);
                    } else {
                        UUID volunteerId = insertVolunteer(connection, userId, account.index);
                        insertPreferences(connection, volunteerId);
                        insertAvailability(connection, volunteerId);
                        insertSyntheticVerifiedCertificates(connection, volunteerId, account.index);
                    }
                }
                connection.commit();
            } catch (RuntimeException | SQLException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }

        System.out.println("role,email,password");
        for (DemoAccount account : accounts) {
            System.out.printf(Locale.ROOT, "%s,%s,%s%n", account.role, account.email, account.password);
        }
        System.out.println("Demo-only synthetic accounts created. Keep these credentials local and delete the output after use.");
    }

    private static void ensureEmailsAvailable(Connection connection, List<DemoAccount> accounts) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)")) {
            for (DemoAccount account : accounts) {
                statement.setString(1, account.email);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        throw new IllegalStateException("Demo account email already exists: " + account.email
                                + ". No accounts were created.");
                    }
                }
            }
        }
    }

    private static UUID insertUser(Connection connection, DemoAccount account, int bcryptCost) throws SQLException {
        UUID userId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users (id, email, password_hash, role, account_status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, account.email);
            statement.setString(3, Passwords.hash(account.password, bcryptCost));
            statement.setString(4, account.role);
            statement.executeUpdate();
        }
        return userId;
    }

    private static void insertElder(Connection connection, UUID userId, int index) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO elders (
                    id, user_id, full_name, date_of_birth, gender, phone, address, locality, city, state, country,
                    pin_code, emergency_contact_name, emergency_contact_phone
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, String.format(Locale.ROOT, "Demo Elder %02d", index));
            statement.setDate(4, Date.valueOf(LocalDate.now().minusYears(65 + index % 20)));
            statement.setString(5, index % 2 == 0 ? "FEMALE" : "MALE");
            statement.setString(6, phone(1, index));
            statement.setString(7, String.format(Locale.ROOT, "Demo residence %02d, Vyttila", index));
            statement.setString(8, "Vyttila");
            statement.setString(9, "Ernakulam");
            statement.setString(10, "Kerala");
            statement.setString(11, "India");
            statement.setString(12, "682019");
            statement.setString(13, String.format(Locale.ROOT, "Demo contact %02d", index));
            statement.setString(14, phone(3, index));
            statement.executeUpdate();
        }
    }

    private static UUID insertVolunteer(Connection connection, UUID userId, int index) throws SQLException {
        UUID volunteerId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO volunteers (
                    id, user_id, full_name, date_of_birth, gender, phone, address, locality, city, state, country,
                    pin_code, educational_qualification, skills, experience_years, preferred_service_radius_km,
                    offers_driving, driving_licence_number, driving_licence_expiry
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setObject(1, volunteerId);
            statement.setObject(2, userId);
            statement.setString(3, String.format(Locale.ROOT, "Demo Volunteer %02d", index));
            statement.setDate(4, Date.valueOf(LocalDate.now().minusYears(20 + index % 35)));
            statement.setString(5, index % 2 == 0 ? "FEMALE" : "MALE");
            statement.setString(6, phone(2, index));
            statement.setString(7, String.format(Locale.ROOT, "Demo residence %02d, Main Road", index));
            statement.setString(8, "Kodakara");
            statement.setString(9, "Kodakara");
            statement.setString(10, "Kerala");
            statement.setString(11, "India");
            statement.setString(12, "680683");
            statement.setString(13, "Bachelor degree");
            statement.setString(14, "grocery shopping, transportation, companionship, technology support, booking, home nursing, first aid, pet care, home maintenance");
            statement.setInt(15, 1 + index % 10);
            statement.setBigDecimal(16, new java.math.BigDecimal("50.00"));
            statement.setBoolean(17, true);
            statement.setString(18, String.format(Locale.ROOT, "DEMO-LICENCE-%04d", index));
            statement.setDate(19, Date.valueOf(LocalDate.now().plusYears(2)));
            statement.executeUpdate();
        }
        return volunteerId;
    }

    private static void insertPreferences(Connection connection, UUID volunteerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO volunteer_service_preferences (volunteer_id, assistance_type) VALUES (?, ?)")) {
            for (String type : ASSISTANCE_TYPES) {
                statement.setObject(1, volunteerId);
                statement.setString(2, type);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertAvailability(Connection connection, UUID volunteerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO volunteer_availability (volunteer_id, weekday, start_time, end_time)
                VALUES (?, ?, ?, ?)
                """)) {
            for (int weekday = 1; weekday <= 7; weekday++) {
                statement.setObject(1, volunteerId);
                statement.setInt(2, weekday);
                statement.setTime(3, Time.valueOf(LocalTime.of(8, 0)));
                statement.setTime(4, Time.valueOf(LocalTime.of(20, 0)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertSyntheticVerifiedCertificates(Connection connection, UUID volunteerId, int index)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO volunteer_certificates (
                    volunteer_id, certificate_type, title, verification_status
                ) VALUES (?, ?, ?, 'VERIFIED')
                """)) {
            for (String type : List.of("TENTH_STANDARD", "QUALIFICATION", "DRIVING_LICENCE", "HEALTHCARE", "FIRST_AID")) {
                statement.setObject(1, volunteerId);
                statement.setString(2, type);
                statement.setString(3, String.format(Locale.ROOT, "DEMO ONLY - synthetic %s record %02d", type, index));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String email(String role, int index) {
        return String.format(Locale.ROOT, "%s.demo%02d@vayomitra.test", role, index);
    }

    private static String phone(int group, int index) {
        return String.format(Locale.ROOT, "600%d%06d", group, index);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "Vm7-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record DemoAccount(String role, String email, String password, int index) {
    }
}