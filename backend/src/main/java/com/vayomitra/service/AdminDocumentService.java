
package com.vayomitra.service;

import com.vayomitra.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class AdminDocumentService {

    private final Database database;

    public AdminDocumentService(Database database) {
        this.database = database;
    }

    public List<Map<String, Object>> getPendingDocuments()
            throws SQLException {

        String sql = """
            SELECT 'ELDER' AS owner_type,
                   document_id AS id,
                   elder_id AS owner_id,
                   document_type,
                   document_name,
                   verification_status,
                   uploaded_at
            FROM elder_documents
            WHERE UPPER(verification_status) = 'PENDING'

            UNION ALL

            SELECT 'VOLUNTEER' AS owner_type,
                   document_id AS id,
                   volunteer_id AS owner_id,
                   document_type,
                   document_name,
                   verification_status,
                   uploaded_at
            FROM volunteer_documents
            WHERE UPPER(verification_status) = 'PENDING'

            UNION ALL

            SELECT 'VOLUNTEER_CERTIFICATE' AS owner_type,
                   certificate_id AS id,
                   volunteer_id AS owner_id,
                   certificate_type AS document_type,
                   certificate_name AS document_name,
                   verification_status,
                   uploaded_at
            FROM volunteer_certificates
            WHERE UPPER(verification_status) = 'PENDING'

            ORDER BY uploaded_at ASC
            """;

        List<Map<String, Object>> documents = new ArrayList<>();

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> document = new LinkedHashMap<>();

                document.put("ownerType", rs.getString("owner_type"));
                document.put("documentId", rs.getLong("id"));
                document.put("ownerId", rs.getLong("owner_id"));
                document.put("documentType", rs.getString("document_type"));
                document.put("documentName", rs.getString("document_name"));
                document.put("verificationStatus",
                        rs.getString("verification_status"));
                document.put("uploadedAt", rs.getTimestamp("uploaded_at"));

                documents.add(document);
            }
        }

        return documents;
    }

    public void updateDocumentStatus(
            String ownerType,
            long documentId,
            String newStatus) throws SQLException {

        if (newStatus == null) {
            throw new IllegalArgumentException(
                    "Status must be VERIFIED or REJECTED");
        }

        newStatus = newStatus.toUpperCase(Locale.ROOT);

        if (!"VERIFIED".equals(newStatus)
                && !"REJECTED".equals(newStatus)) {
            throw new IllegalArgumentException(
                    "Status must be VERIFIED or REJECTED");
        }

        String sql;

        switch (ownerType == null ? "" : ownerType.toUpperCase(Locale.ROOT)) {
            case "ELDER" -> sql = """
                UPDATE elder_documents
                SET verification_status = ?
                WHERE document_id = ?
                """;

            case "VOLUNTEER" -> sql = """
                UPDATE volunteer_documents
                SET verification_status = ?
                WHERE document_id = ?
                """;

            case "VOLUNTEER_CERTIFICATE" -> sql = """
                UPDATE volunteer_certificates
                SET verification_status = ?
                WHERE certificate_id = ?
                """;

            default -> throw new IllegalArgumentException(
                    "Invalid document owner type");
        }

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, newStatus);
            statement.setLong(2, documentId);

            int updated = statement.executeUpdate();

            if (updated == 0) {
                throw new SQLException("Document not found");
            }
        }
    }
}
