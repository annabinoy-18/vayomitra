package com.vayomitra.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;
import com.vayomitra.security.AuthUser;
import com.vayomitra.util.Geo;
import com.vayomitra.util.Jsons;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MatchingService {
    private static final int MAX_RECOMMENDATIONS = 10;
    private final Database database;
    private final AppConfig config;

    public MatchingService(Database database) {
        this.database = database;
        this.config = database.config();
    }

    public List<ScoredVolunteer> rebuildRecommendations(Connection connection, UUID requestId) throws SQLException {
        RequestSnapshot request = loadRequest(connection, requestId);
        List<Candidate> candidates = loadCandidates(connection, request.assistanceType);
        List<ScoredVolunteer> eligible = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Exclusion exclusion = exclude(connection, request, candidate);
            if (exclusion != null) {
                continue;
            }
            eligible.add(score(request, candidate));
        }
        eligible.sort(Comparator.comparingDouble(ScoredVolunteer::totalScore).reversed());
        if (eligible.size() > MAX_RECOMMENDATIONS) {
            eligible = new ArrayList<>(eligible.subList(0, MAX_RECOMMENDATIONS));
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM match_recommendations WHERE request_id = ?")) {
            delete.setObject(1, requestId);
            delete.executeUpdate();
        }
        int rank = 1;
        for (ScoredVolunteer scored : eligible) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO match_recommendations (
                        request_id, volunteer_id, total_score, skill_score, distance_score,
                        availability_score, qualification_score, distance_km, explanation,
                        missing_information, rank_position
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                insert.setObject(1, requestId);
                insert.setObject(2, scored.volunteerId);
                insert.setBigDecimal(3, decimal(scored.totalScore));
                insert.setBigDecimal(4, decimal(scored.skillScore));
                insert.setBigDecimal(5, decimal(scored.distanceScore));
                insert.setBigDecimal(6, decimal(scored.availabilityScore));
                insert.setBigDecimal(7, decimal(scored.qualificationScore));
                if (scored.distanceKm == null) {
                    insert.setNull(8, java.sql.Types.NUMERIC);
                } else {
                    insert.setBigDecimal(8, decimal(scored.distanceKm));
                }
                insert.setString(9, scored.explanation);
                insert.setString(10, scored.missingInformation);
                insert.setInt(11, rank++);
                insert.executeUpdate();
            }
        }
        if (!eligible.isEmpty()) {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE assistance_requests SET status = 'MATCHED'
                    WHERE id = ? AND status = 'PENDING'
                    """)) {
                update.setObject(1, requestId);
                if (update.executeUpdate() > 0) {
                    try (PreparedStatement history = connection.prepareStatement("""
                            INSERT INTO request_status_history (request_id, old_status, new_status, note)
                            VALUES (?, 'PENDING', 'MATCHED', 'Eligible volunteer matches generated')
                            """)) {
                        history.setObject(1, requestId);
                        history.executeUpdate();
                    }
                }
            }
        }
        return eligible;
    }

    public ObjectNode listRecommendations(UUID requestId) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                    SELECT m.*, v.full_name, v.phone, u.email, v.city,
                        v.experience_years, v.educational_qualification, v.skills
                     FROM match_recommendations m
                     JOIN volunteers v ON v.id = m.volunteer_id
                    JOIN users u ON u.id = v.user_id AND u.account_status = 'ACTIVE'
                     WHERE m.request_id = ?
                     ORDER BY m.rank_position ASC
                     """)) {
            statement.setObject(1, requestId);
            ArrayNode items = Jsons.MAPPER.createArrayNode();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ObjectNode item = Jsons.object();
                    item.put("volunteerId", rs.getObject("volunteer_id", UUID.class).toString());
                    item.put("fullName", rs.getString("full_name"));
                    item.put("phone", rs.getString("phone"));
                    item.put("email", rs.getString("email"));
                    item.put("city", rs.getString("city"));
                    item.put("experienceYears", rs.getInt("experience_years"));
                    item.put("educationalQualification", rs.getString("educational_qualification"));
                    item.put("skills", rs.getString("skills"));
                    item.put("totalScore", rs.getBigDecimal("total_score"));
                    item.put("skillScore", rs.getBigDecimal("skill_score"));
                    item.put("distanceScore", rs.getBigDecimal("distance_score"));
                    item.put("availabilityScore", rs.getBigDecimal("availability_score"));
                    item.put("qualificationScore", rs.getBigDecimal("qualification_score"));
                    BigDecimal km = rs.getBigDecimal("distance_km");
                    if (km == null) {
                        item.putNull("distanceKm");
                    } else {
                        item.put("distanceKm", km);
                    }
                    item.put("explanation", rs.getString("explanation"));
                    item.put("missingInformation", rs.getString("missing_information"));
                    item.put("rank", rs.getInt("rank_position"));
                    items.add(item);
                }
            }
            ObjectNode data = Jsons.object();
            data.set("matches", items);
            data.put("skillWeight", config.skillWeight());
            data.put("distanceWeight", config.distanceWeight());
            data.put("availabilityWeight", config.availabilityWeight());
            data.put("qualificationWeight", config.qualificationWeight());
            return Jsons.ok(data);
        }
    }

    public ObjectNode eligibilitySummary(Connection connection, UUID requestId) throws SQLException {
        RequestSnapshot request = loadRequest(connection, requestId);
        List<Candidate> candidates = loadCandidates(connection, request.assistanceType);
        Map<String, Integer> excluded = new LinkedHashMap<>();
        int eligibleCount = 0;
        for (Candidate candidate : candidates) {
            Exclusion reason = exclude(connection, request, candidate);
            if (reason == null) {
                eligibleCount++;
            } else {
                excluded.merge(reason.reason, 1, Integer::sum);
            }
        }
        ObjectNode summary = Jsons.object();
        summary.put("candidateCount", candidates.size());
        summary.put("eligibleCount", eligibleCount);
        summary.put("shownCount", Math.min(eligibleCount, MAX_RECOMMENDATIONS));
        ObjectNode reasons = Jsons.object();
        excluded.forEach(reasons::put);
        summary.set("excludedByReason", reasons);
        return summary;
    }

    public ObjectNode volunteerReadiness(AuthUser user) throws SQLException {
        if (!user.isVolunteer()) {
            throw com.vayomitra.util.ApiException.forbidden("Only volunteers can view volunteer matching readiness.");
        }
        try (Connection connection = database.connection();
             PreparedStatement profile = connection.prepareStatement("""
                     SELECT id, offers_driving, driving_licence_expiry
                     FROM volunteers WHERE user_id = ?
                     """)) {
            profile.setObject(1, user.userId());
            try (ResultSet rs = profile.executeQuery()) {
                if (!rs.next()) {
                    throw com.vayomitra.util.ApiException.forbidden("Volunteer profile not found.");
                }
                UUID volunteerId = rs.getObject("id", UUID.class);
                boolean offersDriving = rs.getBoolean("offers_driving");
                Date licenceExpiry = rs.getDate("driving_licence_expiry");
                Set<String> verifiedCertificates = new java.util.HashSet<>();
                try (PreparedStatement certificates = connection.prepareStatement("""
                        SELECT certificate_type FROM volunteer_certificates
                        WHERE volunteer_id = ? AND verification_status = 'VERIFIED'
                        """)) {
                    certificates.setObject(1, volunteerId);
                    try (ResultSet certificateRows = certificates.executeQuery()) {
                        while (certificateRows.next()) {
                            verifiedCertificates.add(certificateRows.getString("certificate_type"));
                        }
                    }
                }
                ObjectNode readiness = Jsons.object();
                readiness.put("tenthCertificateVerified", verifiedCertificates.contains("TENTH_STANDARD"));
                readiness.put("qualificationCertificateVerified", verifiedCertificates.contains("QUALIFICATION"));
                readiness.put("mandatoryCertificatesVerified", verifiedCertificates.contains("TENTH_STANDARD")
                        && verifiedCertificates.contains("QUALIFICATION"));
                readiness.put("offersDriving", offersDriving);
                readiness.put("drivingLicenceVerified", verifiedCertificates.contains("DRIVING_LICENCE"));
                readiness.put("drivingLicenceExpired", licenceExpiry != null
                        && licenceExpiry.toLocalDate().isBefore(LocalDate.now()));
                readiness.put("healthcareCertificateVerified", verifiedCertificates.contains("HEALTHCARE"));
                readiness.put("firstAidCertificateVerified", verifiedCertificates.contains("FIRST_AID"));
                ArrayNode types = readiness.putArray("assistanceTypes");
                try (PreparedStatement preferences = connection.prepareStatement("""
                        SELECT assistance_type FROM volunteer_service_preferences
                        WHERE volunteer_id = ? ORDER BY assistance_type
                        """)) {
                    preferences.setObject(1, volunteerId);
                    try (ResultSet preferenceRows = preferences.executeQuery()) {
                        while (preferenceRows.next()) {
                            types.add(preferenceRows.getString("assistance_type"));
                        }
                    }
                }
                return Jsons.ok(readiness);
            }
        }
    }

    private Exclusion exclude(Connection connection, RequestSnapshot request, Candidate candidate) throws SQLException {
        if (!"ACTIVE".equals(candidate.accountStatus)) {
            return new Exclusion("INACTIVE_OR_SUSPENDED");
        }
        if (!candidate.types.contains(request.assistanceType)) {
            return new Exclusion("ASSISTANCE_TYPE_NOT_SUPPORTED");
        }
        if (!available(connection, candidate.volunteerId, request.preferredDate, request.start, request.end)) {
            return new Exclusion("UNAVAILABLE_FOR_REQUESTED_TIME");
        }
        int active = activeAssignments(connection, candidate.volunteerId);
        if (active >= candidate.maxActiveAssignments) {
            return new Exclusion("ASSIGNMENT_CAPACITY_EXCEEDED");
        }
        if (!verified(connection, candidate.volunteerId, "TENTH_STANDARD")
                || !verified(connection, candidate.volunteerId, "QUALIFICATION")) {
            return new Exclusion("MANDATORY_CERTIFICATES_NOT_VERIFIED");
        }
        if (request.requiresDriving) {
            if (!candidate.offersDriving) {
                return new Exclusion("DRIVING_NOT_OFFERED");
            }
            if (candidate.licenceExpiry != null && candidate.licenceExpiry.isBefore(LocalDate.now())) {
                return new Exclusion("DRIVING_LICENCE_EXPIRED");
            }
            if (!verified(connection, candidate.volunteerId, "DRIVING_LICENCE")) {
                return new Exclusion("DRIVING_LICENCE_NOT_VERIFIED");
            }
        }
        if ("HOME_NURSE_ASSISTANCE".equals(request.assistanceType)
                && !verified(connection, candidate.volunteerId, "HEALTHCARE")) {
            return new Exclusion("HEALTHCARE_CERTIFICATE_REQUIRED");
        }
        if ("EMERGENCY_ASSISTANCE".equals(request.assistanceType)
                && !verified(connection, candidate.volunteerId, "FIRST_AID")) {
            return new Exclusion("FIRST_AID_CERTIFICATE_REQUIRED");
        }
        if (request.hasCoordinates() && candidate.hasCoordinates()) {
            double km = Geo.haversineKm(request.latitude, request.longitude, candidate.latitude, candidate.longitude);
            if (km > candidate.radiusKm) {
                return new Exclusion("OUTSIDE_PREFERRED_RADIUS");
            }
        } else if (!sameArea(request, candidate)) {
            return new Exclusion("LOCATION_UNKNOWN_AND_AREA_MISMATCH");
        }
        return null;
    }

    private ScoredVolunteer score(RequestSnapshot request, Candidate candidate) {
        List<String> missing = new ArrayList<>();
        double skill = skillScore(request, candidate);
        Double distanceKm = null;
        double distance;
        if (request.hasCoordinates() && candidate.hasCoordinates()) {
            distanceKm = Geo.haversineKm(request.latitude, request.longitude, candidate.latitude, candidate.longitude);
            distance = Math.max(0, 100.0 * (1.0 - (distanceKm / candidate.radiusKm)));
        } else {
            missing.add("LOCATION");
            distance = 35.0;
        }
        double availability = 85.0;
        if (candidate.experienceYears <= 0) {
            missing.add("EXPERIENCE");
        }
        double qualification = qualificationScore(candidate, request.assistanceType, missing);
        double total = skill * config.skillWeight()
                + distance * config.distanceWeight()
                + availability * config.availabilityWeight()
                + qualification * config.qualificationWeight();
        total = round(total);
        String explanation = "Skill " + round(skill) + "/100, distance " + round(distance)
                + "/100, availability " + round(availability) + "/100, qualification "
                + round(qualification) + "/100. Weighted total " + total + ".";
        if (distanceKm != null) {
            explanation += " Approximate distance " + round(distanceKm) + " km vs preferred radius "
                    + round(candidate.radiusKm) + " km.";
        } else {
            explanation += " Exact coordinates were unavailable, so distance was scored conservatively.";
        }
        return new ScoredVolunteer(
                candidate.volunteerId,
                total,
                round(skill),
                round(distance),
                round(availability),
                round(qualification),
                distanceKm == null ? null : round(distanceKm),
                explanation,
                missing.isEmpty() ? null : String.join(",", missing)
        );
    }

    private double skillScore(RequestSnapshot request, Candidate candidate) {
        double score = 70.0;
        String haystack = (candidate.skills + " " + candidate.qualification).toLowerCase(Locale.ROOT);
        for (String token : request.assistanceType.toLowerCase(Locale.ROOT).split("_")) {
            if (token.length() > 3 && haystack.contains(token)) {
                score += 8;
            }
        }
        if (request.requiresDriving && candidate.offersDriving) {
            score += 6;
        }
        return Math.min(100.0, score);
    }

    private double qualificationScore(Candidate candidate, String type, List<String> missing) {
        double education = switch (candidate.qualification.toLowerCase(Locale.ROOT)) {
            case String q when q.contains("post") || q.contains("master") || q.contains("nurs") -> 90;
            case String q when q.contains("bachelor") || q.contains("graduate") || q.contains("diploma") -> 75;
            case String q when q.contains("12") || q.contains("higher") -> 60;
            default -> 50;
        };
        double experience = Math.min(100.0, candidate.experienceYears * 8.0);
        if (candidate.experienceYears <= 0) {
            experience = 20.0;
        }
        double typeBoost = 0;
        if ("HOME_NURSE_ASSISTANCE".equals(type) || "EMERGENCY_ASSISTANCE".equals(type)) {
            typeBoost = 15;
        }
        return Math.min(100.0, education * 0.45 + experience * 0.40 + typeBoost);
    }

    private boolean available(Connection connection, UUID volunteerId, LocalDate date, LocalTime start, LocalTime end)
            throws SQLException {
        int isoWeekday = date.getDayOfWeek().getValue();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM volunteer_availability
                WHERE volunteer_id = ?
                  AND weekday = ?
                  AND start_time <= ?
                  AND end_time >= ?
                """)) {
            statement.setObject(1, volunteerId);
            statement.setInt(2, isoWeekday);
            statement.setTime(3, Time.valueOf(start));
            statement.setTime(4, Time.valueOf(end));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int activeAssignments(Connection connection, UUID volunteerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM volunteer_assignments
                WHERE volunteer_id = ? AND status IN ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS')
                """)) {
            statement.setObject(1, volunteerId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean verified(Connection connection, UUID volunteerId, String certificateType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM volunteer_certificates
                WHERE volunteer_id = ? AND certificate_type = ? AND verification_status = 'VERIFIED'
                """)) {
            statement.setObject(1, volunteerId);
            statement.setString(2, certificateType);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean sameArea(RequestSnapshot request, Candidate candidate) {
        if (request.pinCode != null && request.pinCode.equals(candidate.pinCode)) {
            return true;
        }
        return request.city != null && request.city.equalsIgnoreCase(candidate.city);
    }

    private RequestSnapshot loadRequest(Connection connection, UUID requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM assistance_requests WHERE id = ?")) {
            statement.setObject(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Request not found: " + requestId);
                }
                Double lat = rs.getDouble("latitude");
                if (rs.wasNull()) {
                    lat = null;
                }
                Double lng = rs.getDouble("longitude");
                if (rs.wasNull()) {
                    lng = null;
                }
                return new RequestSnapshot(
                        requestId,
                        rs.getString("assistance_type"),
                        rs.getBoolean("requires_driving"),
                        rs.getDate("preferred_date").toLocalDate(),
                        rs.getTime("preferred_time_start").toLocalTime(),
                        rs.getTime("preferred_time_end").toLocalTime(),
                        rs.getString("city"),
                        rs.getString("pin_code"),
                        lat,
                        lng
                );
            }
        }
    }

    private List<Candidate> loadCandidates(Connection connection, String assistanceType) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.*, u.account_status
                FROM volunteers v
                JOIN users u ON u.id = v.user_id
                WHERE u.account_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')
                """)) {
            List<Candidate> candidates = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID volunteerId = rs.getObject("id", UUID.class);
                    Double lat = rs.getDouble("latitude");
                    if (rs.wasNull()) {
                        lat = null;
                    }
                    Double lng = rs.getDouble("longitude");
                    if (rs.wasNull()) {
                        lng = null;
                    }
                    Date expiry = rs.getDate("driving_licence_expiry");
                    candidates.add(new Candidate(
                            volunteerId,
                            rs.getString("account_status"),
                            loadTypes(connection, volunteerId),
                            rs.getString("skills"),
                            rs.getString("educational_qualification"),
                            rs.getInt("experience_years"),
                            rs.getDouble("preferred_service_radius_km"),
                            rs.getBoolean("offers_driving"),
                            expiry == null ? null : expiry.toLocalDate(),
                            rs.getInt("max_active_assignments"),
                            rs.getString("city"),
                            rs.getString("pin_code"),
                            lat,
                            lng
                    ));
                }
            }
            return candidates;
        }
    }

    private Set<String> loadTypes(Connection connection, UUID volunteerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT assistance_type FROM volunteer_service_preferences WHERE volunteer_id = ?")) {
            statement.setObject(1, volunteerId);
            java.util.HashSet<String> types = new java.util.HashSet<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    types.add(rs.getString("assistance_type"));
                }
            }
            return types;
        }
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public record ScoredVolunteer(
            UUID volunteerId,
            double totalScore,
            double skillScore,
            double distanceScore,
            double availabilityScore,
            double qualificationScore,
            Double distanceKm,
            String explanation,
            String missingInformation
    ) {
    }

    private record RequestSnapshot(
            UUID id,
            String assistanceType,
            boolean requiresDriving,
            LocalDate preferredDate,
            LocalTime start,
            LocalTime end,
            String city,
            String pinCode,
            Double latitude,
            Double longitude
    ) {
        boolean hasCoordinates() {
            return Geo.valid(latitude, longitude);
        }
    }

    private record Candidate(
            UUID volunteerId,
            String accountStatus,
            Set<String> types,
            String skills,
            String qualification,
            int experienceYears,
            double radiusKm,
            boolean offersDriving,
            LocalDate licenceExpiry,
            int maxActiveAssignments,
            String city,
            String pinCode,
            Double latitude,
            Double longitude
    ) {
        boolean hasCoordinates() {
            return Geo.valid(latitude, longitude);
        }
    }

    private record Exclusion(String reason) {
    }
}
