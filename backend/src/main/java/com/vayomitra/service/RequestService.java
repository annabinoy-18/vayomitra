package com.vayomitra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vayomitra.db.Database;
import com.vayomitra.security.AuthUser;
import com.vayomitra.util.ApiException;
import com.vayomitra.util.Jsons;
import com.vayomitra.util.Validation;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class RequestService {
    private static final Set<String> OPEN_STATUSES = Set.of("PENDING", "MATCHED");
    private static final Set<String> CANCELABLE = Set.of("PENDING", "MATCHED", "ASSIGNED", "ACCEPTED");
    private final Database database;
    private final AuthService authService;
    private final MatchingService matchingService;
    private final SecureRandom random = new SecureRandom();

    public RequestService(Database database, AuthService authService, MatchingService matchingService) {
        this.database = database;
        this.authService = authService;
        this.matchingService = matchingService;
    }

    public ObjectNode create(AuthUser user, JsonNode body) throws SQLException {
        if (!user.isElder()) {
            throw ApiException.forbidden("Only elders can create assistance requests.");
        }
        Validation v = new Validation();
        String type = Validation.text(body, "assistanceType").toUpperCase(Locale.ROOT);
        String title = Validation.text(body, "title");
        String description = Validation.text(body, "description");
        String dateRaw = Validation.text(body, "preferredDate");
        String startRaw = Validation.text(body, "preferredTimeStart");
        String endRaw = Validation.text(body, "preferredTimeEnd");
        String address = Validation.text(body, "serviceAddress");
        String locality = Validation.text(body, "locality");
        String city = Validation.text(body, "city");
        String state = Validation.text(body, "state");
        String pin = Validation.text(body, "pinCode");
        String priority = defaultPriority(Validation.text(body, "priority"));
        String additional = Validation.text(body, "additionalDetails");
        Double lat = Validation.optionalDouble(body, "latitude");
        Double lng = Validation.optionalDouble(body, "longitude");
        v.assistanceType("assistanceType", type);
        v.require("title", title, 4, 160);
        v.require("description", description, 10, 2000);
        LocalDate date = v.date("preferredDate", dateRaw);
        LocalTime start = v.time("preferredTimeStart", startRaw);
        LocalTime end = v.time("preferredTimeEnd", endRaw);
        v.require("serviceAddress", address, 5, 500);
        if (date != null && date.isBefore(LocalDate.now())) {
            v.errors().put("preferredDate", "Preferred date cannot be in the past.");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            v.errors().put("preferredTimeEnd", "End time must be after start time.");
        }
        if (!pin.isBlank()) {
            v.pin("pinCode", pin);
        }
        boolean hasCoordinates = lat != null && lng != null;
        if (!hasCoordinates && city.isBlank() && pin.isBlank()) {
            v.errors().put("city", "Enter a city or PIN code, or share your location, so volunteers can be matched.");
        }
        if (!Set.of("LOW", "NORMAL", "HIGH", "URGENT").contains(priority)) {
            v.errors().put("priority", "Priority must be LOW, NORMAL, HIGH, or URGENT.");
        }
        v.coordinates(lat, lng);
        boolean requiresDriving = body.path("requiresDriving").asBoolean(false) || "TRANSPORTATION".equals(type);
        v.throwIfInvalid();

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                UUID elderId = authService.elderId(connection, user.userId());
                UUID requestId = UUID.randomUUID();
                String publicId = newPublicId(connection);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO assistance_requests (
                            id, public_id, elder_id, assistance_type, title, description, preferred_date,
                            preferred_time_start, preferred_time_end, service_address, locality, city, state, pin_code,
                            latitude, longitude, priority, requires_driving, additional_details, status
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'PENDING')
                        """)) {
                    statement.setObject(1, requestId);
                    statement.setString(2, publicId);
                    statement.setObject(3, elderId);
                    statement.setString(4, type);
                    statement.setString(5, title);
                    statement.setString(6, description);
                    statement.setDate(7, Date.valueOf(date));
                    statement.setTime(8, Time.valueOf(start));
                    statement.setTime(9, Time.valueOf(end));
                    statement.setString(10, address);
                    statement.setString(11, blankToNull(locality));
                    statement.setString(12, blankToNull(city));
                    statement.setString(13, blankToNull(state));
                    statement.setString(14, blankToNull(pin));
                    setDouble(statement, 15, lat);
                    setDouble(statement, 16, lng);
                    statement.setString(17, priority);
                    statement.setBoolean(18, requiresDriving);
                    statement.setString(19, blankToNull(additional));
                    statement.executeUpdate();
                }
                insertHistory(connection, requestId, null, "PENDING", user.userId(), "Request created");
                matchingService.rebuildRecommendations(connection, requestId);
                connection.commit();
                return get(user, requestId);
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public ObjectNode list(AuthUser user, String scope) throws SQLException {
        try (Connection connection = database.connection()) {
            if (user.isElder()) {
                UUID elderId = authService.elderId(connection, user.userId());
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT r.*, a.status AS assignment_status, v.full_name AS volunteer_name,
                               (SELECT COUNT(*) FROM match_recommendations m WHERE m.request_id = r.id)
                                   AS eligible_volunteer_count
                        FROM assistance_requests r
                        LEFT JOIN volunteer_assignments a ON a.request_id = r.id AND a.status <> 'DECLINED'
                        LEFT JOIN volunteers v ON v.id = a.volunteer_id
                        WHERE r.elder_id = ?
                        ORDER BY r.created_at DESC
                        """)) {
                    statement.setObject(1, elderId);
                    return Jsons.ok(readRequestArray(statement, false));
                }
            }
            if (user.isVolunteer()) {
                UUID volunteerId = authService.volunteerId(connection, user.userId());
                if ("assignments".equals(scope)) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT r.*, a.status AS assignment_status, a.id AS assignment_id
                            FROM volunteer_assignments a
                            JOIN assistance_requests r ON r.id = a.request_id
                            WHERE a.volunteer_id = ? AND a.status <> 'DECLINED'
                            ORDER BY a.assigned_at DESC
                            """)) {
                        statement.setObject(1, volunteerId);
                        return Jsons.ok(readRequestArray(statement, true));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT r.*, m.total_score
                        FROM assistance_requests r
                        JOIN match_recommendations m ON m.request_id = r.id AND m.volunteer_id = ?
                        WHERE r.status IN ('PENDING', 'MATCHED')
                        ORDER BY m.total_score DESC, r.created_at DESC
                        """)) {
                    statement.setObject(1, volunteerId);
                    return Jsons.ok(readRequestArray(statement, true));
                }
            }
            throw ApiException.forbidden("This role cannot list assistance requests.");
        }
    }

    public ObjectNode get(AuthUser user, UUID requestId) throws SQLException {
        try (Connection connection = database.connection()) {
            LoadedRequest loaded = load(connection, requestId);
            authorizeView(connection, user, loaded);
            ObjectNode item = toJson(loaded.rs, user.isVolunteer());
            loaded.rs.close();
            if (user.isElder() || user.isAdmin()) {
                ObjectNode matches = matchingService.listRecommendations(requestId);
                item.set("matches", matches.get("data").get("matches"));
                item.set("matchSummary", matchingService.eligibilitySummary(connection, requestId));
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT a.status, a.assigned_at, a.accepted_at, a.started_at, a.completed_at,
                           v.full_name, v.phone, v.city
                    FROM volunteer_assignments a
                    JOIN volunteers v ON v.id = a.volunteer_id
                    WHERE a.request_id = ? AND a.status <> 'DECLINED'
                    """)) {
                statement.setObject(1, requestId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        ObjectNode assignment = Jsons.object();
                        assignment.put("status", rs.getString("status"));
                        assignment.put("volunteerName", rs.getString("full_name"));
                        assignment.put("volunteerCity", rs.getString("city"));
                        if (user.isElder() || user.isAdmin() || user.isVolunteer()) {
                            assignment.put("volunteerPhone", rs.getString("phone"));
                        }
                        item.set("assignment", assignment);
                    } else {
                        item.putNull("assignment");
                    }
                }
            }
            return Jsons.ok(item);
        }
    }

    public ObjectNode refreshMatches(AuthUser user, UUID requestId) throws SQLException {
        if (!user.isElder() && !user.isAdmin()) {
            throw ApiException.forbidden("Only the elder who created this request or an administrator can refresh matches.");
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                LoadedRequest loaded = load(connection, requestId);
                authorizeView(connection, user, loaded);
                boolean isOpen = OPEN_STATUSES.contains(loaded.status);
                loaded.rs.close();
                if (isOpen) {
                    matchingService.rebuildRecommendations(connection, requestId);
                }
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return get(user, requestId);
    }

    public ObjectNode cancel(AuthUser user, UUID requestId) throws SQLException {
        if (!user.isElder()) {
            throw ApiException.forbidden("Only the elder who created a request can cancel it.");
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                LoadedRequest loaded = load(connection, requestId);
                UUID elderId = authService.elderId(connection, user.userId());
                if (!elderId.equals(loaded.elderId)) {
                    throw ApiException.forbidden("You can only cancel your own requests.");
                }
                String status = loaded.status;
                loaded.rs.close();
                if (!CANCELABLE.contains(status)) {
                    throw ApiException.conflict("This request can no longer be cancelled.");
                }
                updateStatus(connection, requestId, status, "CANCELLED", user.userId(), "Cancelled by elder");
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE volunteer_assignments SET status = 'DECLINED'
                        WHERE request_id = ? AND status IN ('ASSIGNED', 'ACCEPTED')
                        """)) {
                    statement.setObject(1, requestId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return get(user, requestId);
    }

    public ObjectNode assign(AuthUser user, UUID requestId, UUID volunteerId) throws SQLException {
        if (!user.isElder()) {
            throw ApiException.forbidden("Only elders can choose a recommended volunteer.");
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                LoadedRequest loaded = load(connection, requestId);
                UUID elderId = authService.elderId(connection, user.userId());
                if (!elderId.equals(loaded.elderId)) {
                    throw ApiException.forbidden("You can only assign volunteers to your own requests.");
                }
                if (!OPEN_STATUSES.contains(loaded.status)) {
                    throw ApiException.conflict("This request is no longer open for assignment.");
                }
                loaded.rs.close();
                try (PreparedStatement check = connection.prepareStatement("""
                        SELECT 1 FROM match_recommendations WHERE request_id = ? AND volunteer_id = ?
                        """)) {
                    check.setObject(1, requestId);
                    check.setObject(2, volunteerId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            throw ApiException.badRequest("That volunteer is not in the current recommendation list.");
                        }
                    }
                }
                createAssignment(connection, requestId, volunteerId, loaded.status, user.userId());
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return get(user, requestId);
    }

    public ObjectNode apply(AuthUser user, UUID requestId) throws SQLException {
        if (!user.isVolunteer()) {
            throw ApiException.forbidden("Only volunteers can apply for a request.");
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                UUID volunteerId = authService.volunteerId(connection, user.userId());
                LoadedRequest loaded = load(connection, requestId);
                if (!OPEN_STATUSES.contains(loaded.status)) {
                    throw ApiException.conflict("This request is no longer available.");
                }
                loaded.rs.close();
                try (PreparedStatement check = connection.prepareStatement("""
                        SELECT 1 FROM match_recommendations WHERE request_id = ? AND volunteer_id = ?
                        """)) {
                    check.setObject(1, requestId);
                    check.setObject(2, volunteerId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            throw ApiException.forbidden("You are not eligible for this request.");
                        }
                    }
                }
                createAssignment(connection, requestId, volunteerId, loaded.status, user.userId());
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return get(user, requestId);
    }

    public ObjectNode transition(AuthUser user, UUID requestId, String action) throws SQLException {
        if (!user.isVolunteer()) {
            throw ApiException.forbidden("Only the assigned volunteer can update this status.");
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                UUID volunteerId = authService.volunteerId(connection, user.userId());
                LoadedRequest loaded = load(connection, requestId);
                loaded.rs.close();
                AssignmentSnapshot assignment = loadAssignment(connection, requestId);
                if (assignment == null || !assignment.volunteerId.equals(volunteerId)) {
                    throw ApiException.forbidden("You are not assigned to this request.");
                }
                String nextRequest;
                String nextAssignment;
                String note;
                switch (action) {
                    case "accept" -> {
                        require(assignment.status, "ASSIGNED");
                        nextRequest = "ACCEPTED";
                        nextAssignment = "ACCEPTED";
                        note = "Volunteer accepted";
                    }
                    case "start" -> {
                        require(assignment.status, "ACCEPTED");
                        nextRequest = "IN_PROGRESS";
                        nextAssignment = "IN_PROGRESS";
                        note = "Volunteer started";
                    }
                    case "complete" -> {
                        require(assignment.status, "IN_PROGRESS");
                        nextRequest = "COMPLETED";
                        nextAssignment = "COMPLETED";
                        note = "Volunteer completed";
                    }
                    default -> throw ApiException.badRequest("Unsupported status action.");
                }
                updateStatus(connection, requestId, loaded.status, nextRequest, user.userId(), note);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE volunteer_assignments
                        SET status = ?,
                            accepted_at = CASE WHEN ? = 'ACCEPTED' THEN NOW() ELSE accepted_at END,
                            started_at = CASE WHEN ? = 'IN_PROGRESS' THEN NOW() ELSE started_at END,
                            completed_at = CASE WHEN ? = 'COMPLETED' THEN NOW() ELSE completed_at END
                        WHERE id = ?
                        """)) {
                    statement.setString(1, nextAssignment);
                    statement.setString(2, nextAssignment);
                    statement.setString(3, nextAssignment);
                    statement.setString(4, nextAssignment);
                    statement.setObject(5, assignment.id);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return get(user, requestId);
    }

    private void createAssignment(Connection connection, UUID requestId, UUID volunteerId, String oldStatus, UUID actor)
            throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("""
                SELECT status FROM assistance_requests WHERE id = ? FOR UPDATE
                """)) {
            lock.setObject(1, requestId);
            try (ResultSet rs = lock.executeQuery()) {
                if (!rs.next() || !OPEN_STATUSES.contains(rs.getString("status"))) {
                    throw ApiException.conflict("Another volunteer was assigned while this request was open.");
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO volunteer_assignments (request_id, volunteer_id, status) VALUES (?, ?, 'ASSIGNED')
                """)) {
            insert.setObject(1, requestId);
            insert.setObject(2, volunteerId);
            insert.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw ApiException.conflict("This request already has a volunteer assigned.");
            }
            throw e;
        }
        updateStatus(connection, requestId, oldStatus, "ASSIGNED", actor, "Volunteer assigned");
    }

    private void updateStatus(Connection connection, UUID requestId, String oldStatus, String newStatus, UUID actor, String note)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE assistance_requests SET status = ? WHERE id = ?")) {
            statement.setString(1, newStatus);
            statement.setObject(2, requestId);
            statement.executeUpdate();
        }
        insertHistory(connection, requestId, oldStatus, newStatus, actor, note);
    }

    private void insertHistory(Connection connection, UUID requestId, String oldStatus, String newStatus, UUID actor, String note)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO request_status_history (request_id, old_status, new_status, changed_by, note)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, requestId);
            statement.setString(2, oldStatus);
            statement.setString(3, newStatus);
            statement.setObject(4, actor);
            statement.setString(5, note);
            statement.executeUpdate();
        }
    }

    private void authorizeView(Connection connection, AuthUser user, LoadedRequest loaded) throws SQLException {
        if (user.isAdmin()) {
            return;
        }
        if (user.isElder()) {
            UUID elderId = authService.elderId(connection, user.userId());
            if (!elderId.equals(loaded.elderId)) {
                throw ApiException.forbidden("You can only view your own requests.");
            }
            return;
        }
        if (user.isVolunteer()) {
            UUID volunteerId = authService.volunteerId(connection, user.userId());
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT 1 FROM match_recommendations WHERE request_id = ? AND volunteer_id = ?
                    UNION
                    SELECT 1 FROM volunteer_assignments WHERE request_id = ? AND volunteer_id = ?
                    """)) {
                statement.setObject(1, loaded.id);
                statement.setObject(2, volunteerId);
                statement.setObject(3, loaded.id);
                statement.setObject(4, volunteerId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw ApiException.forbidden("You are not allowed to view this request.");
                    }
                }
            }
            return;
        }
        throw ApiException.forbidden("Not allowed.");
    }

    private LoadedRequest load(Connection connection, UUID requestId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT * FROM assistance_requests WHERE id = ?");
        statement.setObject(1, requestId);
        ResultSet rs = statement.executeQuery();
        if (!rs.next()) {
            rs.close();
            statement.close();
            throw ApiException.notFound("Assistance request not found.");
        }
        return new LoadedRequest(
                requestId,
                rs.getObject("elder_id", UUID.class),
                rs.getString("status"),
                rs
        );
    }

    private AssignmentSnapshot loadAssignment(Connection connection, UUID requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, volunteer_id, status FROM volunteer_assignments
                WHERE request_id = ? AND status <> 'DECLINED'
                """)) {
            statement.setObject(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AssignmentSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("volunteer_id", UUID.class),
                        rs.getString("status")
                );
            }
        }
    }

    private ArrayNode readRequestArray(PreparedStatement statement, boolean volunteerView) throws SQLException {
        ArrayNode items = Jsons.MAPPER.createArrayNode();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(toJson(rs, volunteerView));
            }
        }
        return items;
    }

    private ObjectNode toJson(ResultSet rs, boolean volunteerView) throws SQLException {
        ObjectNode item = Jsons.object();
        item.put("id", rs.getObject("id", UUID.class).toString());
        item.put("publicId", rs.getString("public_id"));
        item.put("assistanceType", rs.getString("assistance_type"));
        item.put("title", rs.getString("title"));
        item.put("description", rs.getString("description"));
        item.put("preferredDate", rs.getDate("preferred_date").toLocalDate().toString());
        item.put("preferredTimeStart", rs.getTime("preferred_time_start").toLocalTime().toString());
        item.put("preferredTimeEnd", rs.getTime("preferred_time_end").toLocalTime().toString());
        item.put("serviceAddress", volunteerView || hasColumn(rs, "service_address") ? rs.getString("service_address") : null);
        if (hasColumn(rs, "locality")) {
            item.put("locality", rs.getString("locality"));
            item.put("state", rs.getString("state"));
            item.put("pinCode", rs.getString("pin_code"));
        }
        item.put("city", rs.getString("city"));
        item.put("priority", rs.getString("priority"));
        item.put("requiresDriving", rs.getBoolean("requires_driving"));
        item.put("status", rs.getString("status"));
        if (!volunteerView) {
            putOptionalDouble(item, "latitude", rs, "latitude");
            putOptionalDouble(item, "longitude", rs, "longitude");
        }
        if (hasColumn(rs, "assignment_status")) {
            item.put("assignmentStatus", rs.getString("assignment_status"));
        }
        if (hasColumn(rs, "volunteer_name")) {
            item.put("volunteerName", rs.getString("volunteer_name"));
        }
        if (hasColumn(rs, "total_score")) {
            item.put("matchScore", rs.getBigDecimal("total_score"));
        }
        if (hasColumn(rs, "eligible_volunteer_count")) {
            item.put("eligibleVolunteerCount", rs.getInt("eligible_volunteer_count"));
        }
        return item;
    }

    private boolean hasColumn(ResultSet rs, String label) {
        try {
            rs.findColumn(label);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private void putOptionalDouble(ObjectNode item, String field, ResultSet rs, String column) throws SQLException {
        try {
            rs.findColumn(column);
        } catch (SQLException e) {
            return;
        }
        double value = rs.getDouble(column);
        if (rs.wasNull()) {
            item.putNull(field);
        } else {
            item.put(field, value);
        }
    }

    private String newPublicId(Connection connection) throws SQLException {
        for (int i = 0; i < 8; i++) {
            String candidate = "VM-" + String.format("%06d", random.nextInt(1_000_000));
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM assistance_requests WHERE public_id = ?")) {
                statement.setString(1, candidate);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return candidate;
                    }
                }
            }
        }
        return "VM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void require(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw ApiException.conflict("This action is not allowed from the current status (" + actual + ").");
        }
    }

    private String defaultPriority(String priority) {
        return priority.isBlank() ? "NORMAL" : priority.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void setDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            statement.setDouble(index, value);
        }
    }

    private record LoadedRequest(UUID id, UUID elderId, String status, ResultSet rs) {
    }

    private record AssignmentSnapshot(UUID id, UUID volunteerId, String status) {
    }
}
