package com.vayomitra.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;
import com.vayomitra.http.Multipart;
import com.vayomitra.security.AuthUser;
import com.vayomitra.util.ApiException;
import com.vayomitra.util.Jsons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DocumentService {
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );
    private static final Set<String> VOLUNTEER_DOC_TYPES = Set.of(
            "TENTH_CERTIFICATE", "QUALIFICATION_CERTIFICATE", "DRIVING_LICENCE",
            "HEALTHCARE_CERTIFICATE", "FIRST_AID_CERTIFICATE", "EXPERIENCE_CERTIFICATE","OTHER"
    );
    private final Database database;
    private final AuthService authService;
    private final AppConfig config;

    public DocumentService(Database database, AuthService authService) {
        this.database = database;
        this.authService = authService;
        this.config = database.config();
    }

    public ObjectNode uploadVolunteer(AuthUser user, Map<String, java.util.List<Multipart.Part>> parts) throws Exception {
        if (!user.isVolunteer()) {
            throw ApiException.forbidden("Only volunteers can upload volunteer documents.");
        }
        String documentType = firstText(parts, "documentType").toUpperCase(Locale.ROOT);
        Multipart.Part file = first(parts, "file");
        if (!VOLUNTEER_DOC_TYPES.contains(documentType)) {
            throw ApiException.badRequest("Unsupported document type.");
        }
        if (file == null || file.filename() == null || file.filename().isBlank()) {
            throw ApiException.badRequest("A document file is required.");
        }
        validateFile(file);
        try (Connection connection = database.connection()) {
            UUID volunteerId = authService.volunteerId(connection, user.userId());
            UUID documentId = UUID.randomUUID();
            String stored = documentId + extension(file.filename());
            Path dir = config.uploadDir().resolve("volunteers").resolve(volunteerId.toString());
            Files.createDirectories(dir);
            Path target = dir.resolve(stored);
            Files.write(target, file.data());
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO volunteer_documents (
                        id, volunteer_id, document_type, original_filename, stored_filename, content_type, file_size_bytes
                    ) VALUES (?,?,?,?,?,?,?)
                    """)) {
                statement.setObject(1, documentId);
                statement.setObject(2, volunteerId);
                statement.setString(3, documentType);
                statement.setString(4, safeFilename(file.filename()));
                statement.setString(5, stored);
                statement.setString(6, file.contentType());
                statement.setInt(7, file.data().length);
                statement.executeUpdate();
            }
            linkCertificate(connection, volunteerId, documentId, documentType, file.filename());
            ObjectNode data = Jsons.object();
            data.put("documentId", documentId.toString());
            data.put("verificationStatus", "PENDING");
            data.put("message", "Document uploaded. An administrator must verify it before it counts for matching.");
            return Jsons.ok(data);
        }
    }

    public ObjectNode listMine(AuthUser user) throws SQLException {
        try (Connection connection = database.connection()) {
            if (user.isVolunteer()) {
                UUID volunteerId = authService.volunteerId(connection, user.userId());
                return Jsons.ok(listVolunteerDocs(connection, volunteerId));
            }
            throw ApiException.forbidden("No documents are available for this role.");
        }
    }

    public FilePayload download(AuthUser user, UUID documentId) throws Exception {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT d.*, v.user_id
                     FROM volunteer_documents d
                     JOIN volunteers v ON v.id = d.volunteer_id
                     WHERE d.id = ?
                     """)) {
            statement.setObject(1, documentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("Document not found.");
                }
                UUID owner = rs.getObject("user_id", UUID.class);
                if (!user.isAdmin() && !owner.equals(user.userId())) {
                    throw ApiException.forbidden("You cannot view another person's documents.");
                }
                Path path = config.uploadDir()
                        .resolve("volunteers")
                        .resolve(rs.getObject("volunteer_id", UUID.class).toString())
                        .resolve(rs.getString("stored_filename"));
                if (!Files.isRegularFile(path)) {
                    throw ApiException.notFound("Stored file is missing.");
                }
                return new FilePayload(rs.getString("content_type"), rs.getString("original_filename"), Files.readAllBytes(path));
            }
        }
    }

    public ObjectNode adminPending(AuthUser user) throws SQLException {
        requireAdmin(user);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT d.id, d.document_type, d.original_filename, d.verification_status, d.created_at,
                            v.full_name, u.email
                     FROM volunteer_documents d
                     JOIN volunteers v ON v.id = d.volunteer_id
                     JOIN users u ON u.id = v.user_id
                     WHERE d.verification_status = 'PENDING'
                     ORDER BY d.created_at ASC
                     """);
             ResultSet rs = statement.executeQuery()) {
            ArrayNode items = Jsons.MAPPER.createArrayNode();
            while (rs.next()) {
                ObjectNode item = Jsons.object();
                item.put("documentId", rs.getObject("id", UUID.class).toString());
                item.put("documentType", rs.getString("document_type"));
                item.put("originalFilename", rs.getString("original_filename"));
                item.put("verificationStatus", rs.getString("verification_status"));
                item.put("volunteerName", rs.getString("full_name"));
                item.put("volunteerEmail", rs.getString("email"));
                items.add(item);
            }
            ObjectNode data = Jsons.object();
            data.set("documents", items);
            return Jsons.ok(data);
        }
    }

    public ObjectNode review(AuthUser user, UUID documentId, String status, String notes) throws SQLException {
        requireAdmin(user);
        if (!Set.of("VERIFIED", "REJECTED").contains(status)) {
            throw ApiException.badRequest("Status must be VERIFIED or REJECTED.");
        }
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE volunteer_documents
                        SET verification_status = ?, reviewer_notes = ?
                        WHERE id = ?
                        """)) {
                    statement.setString(1, status);
                    statement.setString(2, notes);
                    statement.setObject(3, documentId);
                    if (statement.executeUpdate() == 0) {
                        throw ApiException.notFound("Document not found.");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE volunteer_certificates
                        SET verification_status = ?
                        WHERE document_id = ?
                        """)) {
                    statement.setString(1, status);
                    statement.setObject(2, documentId);
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
        ObjectNode data = Jsons.object();
        data.put("documentId", documentId.toString());
        data.put("verificationStatus", status);
        return Jsons.ok(data);
    }

    private ArrayNode listVolunteerDocs(Connection connection, UUID volunteerId) throws SQLException {
        ArrayNode items = Jsons.MAPPER.createArrayNode();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, document_type, original_filename, verification_status, reviewer_notes, created_at
                FROM volunteer_documents WHERE volunteer_id = ? ORDER BY created_at DESC
                """)) {
            statement.setObject(1, volunteerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ObjectNode item = Jsons.object();
                    item.put("documentId", rs.getObject("id", UUID.class).toString());
                    item.put("documentType", rs.getString("document_type"));
                    item.put("originalFilename", rs.getString("original_filename"));
                    item.put("verificationStatus", rs.getString("verification_status"));
                    item.put("reviewerNotes", rs.getString("reviewer_notes"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private void linkCertificate(Connection connection, UUID volunteerId, UUID documentId, String documentType, String filename)
            throws SQLException {
        String certificateType = switch (documentType) {
            case "TENTH_CERTIFICATE" -> "TENTH_STANDARD";
            case "QUALIFICATION_CERTIFICATE" -> "QUALIFICATION";
            case "DRIVING_LICENCE" -> "DRIVING_LICENCE";
            case "HEALTHCARE_CERTIFICATE" -> "HEALTHCARE";
            case "FIRST_AID_CERTIFICATE" -> "FIRST_AID";
            case "EXPERIENCE_CERTIFICATE" -> "EXPERIENCE";
            default -> "OTHER";
        };
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO volunteer_certificates (
                    volunteer_id, document_id, certificate_type, title, verification_status
                ) VALUES (?, ?, ?, ?, 'PENDING')
                """)) {
            statement.setObject(1, volunteerId);
            statement.setObject(2, documentId);
            statement.setString(3, certificateType);
            statement.setString(4, filename);
            statement.executeUpdate();
        }
    }

    private void validateFile(Multipart.Part file) {
        if (file.data().length == 0 || file.data().length > config.maxUploadBytes()) {
            throw new ApiException(413, "PAYLOAD_TOO_LARGE", "Each document must be between 1 byte and 5 MB.");
        }
        String filename = safeFilename(file.filename()).toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".pdf") || filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png"))) {
            throw ApiException.badRequest("Only PDF, JPG, and PNG files are allowed.");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw ApiException.badRequest("File name is not allowed.");
        }
        String contentType = file.contentType() == null ? "" : file.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("File type is not allowed.");
        }
    }

    private String safeFilename(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash >= 0 ? filename.substring(slash + 1) : filename;
    }

    private String extension(String filename) {
        String safe = safeFilename(filename).toLowerCase(Locale.ROOT);
        int dot = safe.lastIndexOf('.');
        return dot >= 0 ? safe.substring(dot) : "";
    }

    private void requireAdmin(AuthUser user) {
        if (!user.isAdmin()) {
            throw ApiException.forbidden("Administrator access is required.");
        }
    }

    private Multipart.Part first(Map<String, java.util.List<Multipart.Part>> parts, String name) {
        java.util.List<Multipart.Part> list = parts.get(name);
        return list == null || list.isEmpty() ? null : list.getFirst();
    }

    private String firstText(Map<String, java.util.List<Multipart.Part>> parts, String name) {
        Multipart.Part part = first(parts, name);
        return part == null ? "" : part.asText();
    }

    public record FilePayload(String contentType, String filename, byte[] bytes) {
    }
}
