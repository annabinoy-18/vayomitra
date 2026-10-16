package com.vayomitra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;
import com.vayomitra.security.AuthUser;
import com.vayomitra.security.Passwords;
import com.vayomitra.security.Tokens;
import com.vayomitra.util.ApiException;
import com.vayomitra.util.Jsons;
import com.vayomitra.util.Validation;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AuthService {
    private final Database database;
    private final AppConfig config;

    public AuthService(Database database) {
        this.database = database;
        this.config = database.config();
    }

    public ObjectNode registerElder(JsonNode body) throws SQLException {
        Validation v = new Validation();
        String fullName = Validation.text(body, "fullName");
        String email = Validation.text(body, "email").toLowerCase();
        String password = Validation.text(body, "password");
        String confirm = Validation.text(body, "confirmPassword");
        String dobRaw = Validation.text(body, "dateOfBirth");
        String gender = Validation.text(body, "gender").toUpperCase();
        String phone = Validation.text(body, "phone");
        String address = Validation.text(body, "address");
        String locality = Validation.text(body, "locality");
        String city = Validation.text(body, "city");
        String state = Validation.text(body, "state");
        String country = defaultCountry(Validation.text(body, "country"));
        String pin = Validation.text(body, "pinCode");
        String emergencyName = Validation.text(body, "emergencyContactName");
        String emergencyPhone = Validation.text(body, "emergencyContactPhone");
        Double lat = Validation.optionalDouble(body, "latitude");
        Double lng = Validation.optionalDouble(body, "longitude");

        v.require("fullName", fullName, 2, 120);
        v.email("email", email);
        v.password("password", password);
        v.confirmPassword(password, confirm);
        LocalDate dob = v.dateOfBirth("dateOfBirth", dobRaw, 55, 120);
        v.gender("gender", gender);
        v.phone("phone", phone);
        v.require("address", address, 5, 500);
        v.require("locality", locality, 2, 120);
        v.require("city", city, 2, 80);
        v.require("state", state, 2, 80);
        v.require("country", country, 2, 80);
        v.pin("pinCode", pin);
        v.require("emergencyContactName", emergencyName, 2, 120);
        v.phone("emergencyContactPhone", emergencyPhone);
        v.coordinates(lat, lng);
        v.throwIfInvalid();

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                if (emailTaken(connection, email)) {
                    throw ApiException.conflict("An account with this email already exists.");
                }
                UUID userId = insertUser(connection, email, password, "ELDER");
                UUID elderId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO elders (
                            id, user_id, full_name, date_of_birth, gender, phone, address, locality, city, state, country,
                            pin_code, latitude, longitude, emergency_contact_name, emergency_contact_phone
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    statement.setObject(1, elderId);
                    statement.setObject(2, userId);
                    statement.setString(3, fullName);
                    statement.setDate(4, Date.valueOf(dob));
                    statement.setString(5, gender);
                    statement.setString(6, phone);
                    statement.setString(7, address);
                    statement.setString(8, locality);
                    statement.setString(9, city);
                    statement.setString(10, state);
                    statement.setString(11, country);
                    statement.setString(12, pin);
                    setOptionalDouble(statement, 13, lat);
                    setOptionalDouble(statement, 14, lng);
                    statement.setString(15, emergencyName);
                    statement.setString(16, emergencyPhone);
                    statement.executeUpdate();
                }
                connection.commit();
                ObjectNode data = Jsons.object();
                data.put("userId", userId.toString());
                data.put("role", "ELDER");
                data.put("message", "Elder registration successful. You can now log in.");
                return Jsons.ok(data);
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public ObjectNode registerVolunteer(JsonNode body) throws SQLException {
        Validation v = new Validation();
        String fullName = Validation.text(body, "fullName");
        String email = Validation.text(body, "email").toLowerCase();
        String password = Validation.text(body, "password");
        String confirm = Validation.text(body, "confirmPassword");
        String dobRaw = Validation.text(body, "dateOfBirth");
        String gender = Validation.text(body, "gender").toUpperCase();
        String phone = Validation.text(body, "phone");
        String address = Validation.text(body, "address");
        String locality = Validation.text(body, "locality");
        String city = Validation.text(body, "city");
        String state = Validation.text(body, "state");
        String country = defaultCountry(Validation.text(body, "country"));
        String pin = Validation.text(body, "pinCode");
        Double lat = Validation.optionalDouble(body, "latitude");
        Double lng = Validation.optionalDouble(body, "longitude");
        String qualification = Validation.text(body, "educationalQualification");
        String skills = Validation.text(body, "skills");
        String experienceRaw = Validation.text(body, "experienceYears");
        List<String> types = Validation.stringList(body, "assistanceTypes");
        boolean offersDriving = body.path("offersDriving").asBoolean(false);
        String licenceNumber = Validation.text(body, "drivingLicenceNumber");
        String licenceExpiryRaw = Validation.text(body, "drivingLicenceExpiry");
        String radiusRaw = Validation.text(body, "preferredServiceRadiusKm");

        v.require("fullName", fullName, 2, 120);
        v.email("email", email);
        v.password("password", password);
        v.confirmPassword(password, confirm);
        LocalDate dob = v.dateOfBirth("dateOfBirth", dobRaw, 18, 80);
        v.gender("gender", gender);
        v.phone("phone", phone);
        v.require("address", address, 5, 500);
        v.require("locality", locality, 2, 120);
        v.require("city", city, 2, 80);
        v.require("state", state, 2, 80);
        v.require("country", country, 2, 80);
        v.pin("pinCode", pin);
        v.coordinates(lat, lng);
        v.require("educationalQualification", qualification, 2, 160);
        v.require("skills", skills, 3, 1000);
        if (types.isEmpty()) {
            v.errors().put("assistanceTypes", "Select at least one assistance type you can provide.");
        }
        for (String type : types) {
            if (!Validation.ASSISTANCE_TYPES.contains(type)) {
                v.errors().put("assistanceTypes", "One or more assistance types are not supported.");
            }
        }
        int experience = parseInt(experienceRaw, 0);
        if (experience < 0 || experience > 60) {
            v.errors().put("experienceYears", "Experience must be between 0 and 60 years.");
        }
        double radius = parseDouble(radiusRaw, config.defaultRadiusKm());
        if (radius <= 0 || radius > 200) {
            v.errors().put("preferredServiceRadiusKm", "Service radius must be between 1 and 200 km.");
        }
        LocalDate licenceExpiry = null;
        if (offersDriving) {
            v.require("drivingLicenceNumber", licenceNumber, 6, 40);
            if (licenceExpiryRaw.isBlank()) {
                v.errors().put("drivingLicenceExpiry", "Driving licence expiry is required when offering driving help.");
            } else {
                licenceExpiry = v.date("drivingLicenceExpiry", licenceExpiryRaw);
                if (licenceExpiry != null && licenceExpiry.isBefore(LocalDate.now())) {
                    v.errors().put("drivingLicenceExpiry", "Driving licence appears expired.");
                }
            }
        }
        v.throwIfInvalid();

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                if (emailTaken(connection, email)) {
                    throw ApiException.conflict("An account with this email already exists.");
                }
                UUID userId = insertUser(connection, email, password, "VOLUNTEER");
                UUID volunteerId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO volunteers (
                            id, user_id, full_name, date_of_birth, gender, phone, address, locality, city, state, country,
                            pin_code, latitude, longitude, educational_qualification, skills, experience_years,
                            preferred_service_radius_km, offers_driving, driving_licence_number, driving_licence_expiry
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """)) {
                    statement.setObject(1, volunteerId);
                    statement.setObject(2, userId);
                    statement.setString(3, fullName);
                    statement.setDate(4, Date.valueOf(dob));
                    statement.setString(5, gender);
                    statement.setString(6, phone);
                    statement.setString(7, address);
                    statement.setString(8, locality);
                    statement.setString(9, city);
                    statement.setString(10, state);
                    statement.setString(11, country);
                    statement.setString(12, pin);
                    setOptionalDouble(statement, 13, lat);
                    setOptionalDouble(statement, 14, lng);
                    statement.setString(15, qualification);
                    statement.setString(16, skills);
                    statement.setInt(17, experience);
                    statement.setDouble(18, radius);
                    statement.setBoolean(19, offersDriving);
                    statement.setString(20, offersDriving ? licenceNumber : null);
                    if (licenceExpiry == null) {
                        statement.setNull(21, Types.DATE);
                    } else {
                        statement.setDate(21, Date.valueOf(licenceExpiry));
                    }
                    statement.executeUpdate();
                }
                for (String type : types) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO volunteer_service_preferences (volunteer_id, assistance_type) VALUES (?, ?)
                            """)) {
                        statement.setObject(1, volunteerId);
                        statement.setString(2, type);
                        statement.executeUpdate();
                    }
                }
                JsonNode availability = body.path("availability");
                if (availability.isArray() && availability.size() > 0) {
                    for (JsonNode slot : availability) {
                        int weekday = slot.path("weekday").asInt();
                        LocalTime start = LocalTime.parse(slot.path("start").asText());
                        LocalTime end = LocalTime.parse(slot.path("end").asText());
                        if (weekday < 1 || weekday > 7 || !end.isAfter(start)) {
                            throw ApiException.badRequest("Availability slots must use weekday 1-7 and end after start.");
                        }
                        insertAvailability(connection, volunteerId, weekday, start, end);
                    }
                } else {
                    for (int day = 1; day <= 7; day++) {
                        insertAvailability(connection, volunteerId, day, LocalTime.of(8, 0), LocalTime.of(20, 0));
                    }
                }
                connection.commit();
                ObjectNode data = Jsons.object();
                data.put("userId", userId.toString());
                data.put("role", "VOLUNTEER");
                data.put("message", "Volunteer registration successful. Upload your certificates after login. Matching stays blocked until mandatory documents are verified.");
                return Jsons.ok(data);
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public ObjectNode login(JsonNode body) throws SQLException {
        String email = Validation.text(body, "email").toLowerCase();
        String password = Validation.text(body, "password");
        String requestedRole = Validation.text(body, "role").toUpperCase();
        if (email.isBlank() || password.isBlank()) {
            throw ApiException.unauthorized("Email and password are required.");
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, email, password_hash, role, account_status FROM users WHERE email = ?
                     """)) {
            statement.setString(1, email);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next() || !Passwords.verify(password, rs.getString("password_hash"))) {
                    throw ApiException.unauthorized("Incorrect email or password.");
                }
                if (!"ACTIVE".equals(rs.getString("account_status"))) {
                    throw ApiException.forbidden("This account is not active.");
                }
                String role = rs.getString("role");
                if (!requestedRole.isBlank() && !requestedRole.equals(role)) {
                    throw ApiException.unauthorized("This account is not registered as " + requestedRole + ".");
                }
                UUID userId = rs.getObject("id", UUID.class);
                String token = Tokens.randomToken();
                Instant expires = Instant.now().plusSeconds(config.tokenExpiryHours() * 3600L);
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)
                        """)) {
                    insert.setObject(1, userId);
                    insert.setString(2, Tokens.sha256(token));
                    insert.setObject(3, expires.atOffset(java.time.ZoneOffset.UTC));
                    insert.executeUpdate();
                }
                ObjectNode data = Jsons.object();
                data.put("token", token);
                data.put("expiresAt", expires.toString());
                data.put("role", role);
                data.put("email", rs.getString("email"));
                data.put("userId", userId.toString());
                return Jsons.ok(data);
            }
        }
    }

    public void logout(AuthUser user) throws SQLException {
        if (user == null) {
            return;
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE auth_sessions SET revoked_at = NOW() WHERE id = ? AND revoked_at IS NULL
                     """)) {
            statement.setObject(1, user.sessionId());
            statement.executeUpdate();
        }
    }

    public AuthUser authenticate(String token) throws SQLException {
        if (token == null || token.length() < 32) {
            throw ApiException.unauthorized("Please log in to continue.");
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.id AS session_id, u.id AS user_id, u.email, u.role, u.account_status
                     FROM auth_sessions s
                     JOIN users u ON u.id = s.user_id
                     WHERE s.token_hash = ?
                       AND s.revoked_at IS NULL
                       AND s.expires_at > NOW()
                     """)) {
            statement.setString(1, Tokens.sha256(token));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.unauthorized("Your session expired or is invalid. Please log in again.");
                }
                AuthUser user = new AuthUser(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getString("account_status")
                );
                if (!user.isActive()) {
                    throw ApiException.forbidden("This account is not active.");
                }
                return user;
            }
        }
    }

    public ObjectNode me(AuthUser user) throws SQLException {
        ObjectNode data = Jsons.object();
        data.put("userId", user.userId().toString());
        data.put("email", user.email());
        data.put("role", user.role());
        data.put("accountStatus", user.accountStatus());
        try (Connection connection = database.connection()) {
            if (user.isElder()) {
                fillElder(connection, user.userId(), data, true);
            } else if (user.isVolunteer()) {
                fillVolunteer(connection, user.userId(), data, true);
            }
        }
        return Jsons.ok(data);
    }

    public void fillElder(Connection connection, UUID userId, ObjectNode data, boolean includePrivate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM elders WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                data.put("elderId", rs.getObject("id", UUID.class).toString());
                data.put("fullName", rs.getString("full_name"));
                data.put("dateOfBirth", rs.getDate("date_of_birth").toLocalDate().toString());
                data.put("gender", rs.getString("gender"));
                data.put("phone", rs.getString("phone"));
                data.put("address", rs.getString("address"));
                data.put("locality", rs.getString("locality"));
                data.put("city", rs.getString("city"));
                data.put("state", rs.getString("state"));
                data.put("country", rs.getString("country"));
                data.put("pinCode", rs.getString("pin_code"));
                if (includePrivate) {
                    putDouble(data, "latitude", rs, "latitude");
                    putDouble(data, "longitude", rs, "longitude");
                    data.put("emergencyContactName", rs.getString("emergency_contact_name"));
                    data.put("emergencyContactPhone", rs.getString("emergency_contact_phone"));
                }
            }
        }
    }

    public void fillVolunteer(Connection connection, UUID userId, ObjectNode data, boolean includePrivate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM volunteers WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                UUID volunteerId = rs.getObject("id", UUID.class);
                data.put("volunteerId", volunteerId.toString());
                data.put("fullName", rs.getString("full_name"));
                data.put("dateOfBirth", rs.getDate("date_of_birth").toLocalDate().toString());
                data.put("gender", rs.getString("gender"));
                data.put("phone", includePrivate ? rs.getString("phone") : maskedPhone(rs.getString("phone")));
                data.put("city", rs.getString("city"));
                data.put("state", rs.getString("state"));
                data.put("locality", rs.getString("locality"));
                if (includePrivate) {
                    data.put("address", rs.getString("address"));
                    data.put("country", rs.getString("country"));
                    data.put("pinCode", rs.getString("pin_code"));
                    putDouble(data, "latitude", rs, "latitude");
                    putDouble(data, "longitude", rs, "longitude");
                    data.put("drivingLicenceNumber", rs.getString("driving_licence_number"));
                    Date expiry = rs.getDate("driving_licence_expiry");
                    if (expiry != null) {
                        data.put("drivingLicenceExpiry", expiry.toLocalDate().toString());
                    }
                }
                data.put("educationalQualification", rs.getString("educational_qualification"));
                data.put("skills", rs.getString("skills"));
                data.put("experienceYears", rs.getInt("experience_years"));
                data.put("preferredServiceRadiusKm", rs.getDouble("preferred_service_radius_km"));
                data.put("offersDriving", rs.getBoolean("offers_driving"));
                data.put("maxActiveAssignments", rs.getInt("max_active_assignments"));
                ArrayNode types = data.putArray("assistanceTypes");
                try (PreparedStatement prefs = connection.prepareStatement(
                        "SELECT assistance_type FROM volunteer_service_preferences WHERE volunteer_id = ?")) {
                    prefs.setObject(1, volunteerId);
                    try (ResultSet prs = prefs.executeQuery()) {
                        while (prs.next()) {
                            types.add(prs.getString("assistance_type"));
                        }
                    }
                }
            }
        }
    }

    public UUID elderId(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM elders WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.forbidden("Elder profile not found.");
                }
                return rs.getObject("id", UUID.class);
            }
        }
    }

    public UUID volunteerId(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM volunteers WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.forbidden("Volunteer profile not found.");
                }
                return rs.getObject("id", UUID.class);
            }
        }
    }

    private UUID insertUser(Connection connection, String email, String password, String role) throws SQLException {
        UUID userId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users (id, email, password_hash, role, account_status) VALUES (?, ?, ?, ?, 'ACTIVE')
                """)) {
            statement.setObject(1, userId);
            statement.setString(2, email);
            statement.setString(3, Passwords.hash(password, config.bcryptCost()));
            statement.setString(4, role);
            statement.executeUpdate();
        }
        return userId;
    }

    private boolean emailTaken(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users WHERE email = ?")) {
            statement.setString(1, email);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertAvailability(Connection connection, UUID volunteerId, int weekday, LocalTime start, LocalTime end)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO volunteer_availability (volunteer_id, weekday, start_time, end_time)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setObject(1, volunteerId);
            statement.setInt(2, weekday);
            statement.setTime(3, Time.valueOf(start));
            statement.setTime(4, Time.valueOf(end));
            statement.executeUpdate();
        }
    }

    private void setOptionalDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            statement.setDouble(index, value);
        }
    }

    private void putDouble(ObjectNode data, String field, ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        if (rs.wasNull()) {
            data.putNull(field);
        } else {
            data.put(field, value);
        }
    }

    private String defaultCountry(String country) {
        return country.isBlank() ? "India" : country;
    }

    private int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private String maskedPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "**********";
        }
        return "******" + phone.substring(phone.length() - 4);
    }
}
