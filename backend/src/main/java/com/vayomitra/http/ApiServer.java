package com.vayomitra.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vayomitra.config.AppConfig;
import com.vayomitra.db.Database;
import com.vayomitra.security.AuthUser;
import com.vayomitra.service.AuthService;
import com.vayomitra.service.DocumentService;
import com.vayomitra.service.MatchingService;
import com.vayomitra.service.RequestService;
import com.vayomitra.util.ApiException;
import com.vayomitra.util.Jsons;
import com.vayomitra.util.Validation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiServer {
    private static final Pattern REQUEST_ID = Pattern.compile("^/api/requests/([0-9a-fA-F-]{36})(/.*)?$");
    private static final Pattern DOCUMENT_FILE = Pattern.compile("^/api/documents/([0-9a-fA-F-]{36})/file$");
    private static final Pattern DOCUMENT_REVIEW = Pattern.compile("^/api/admin/documents/([0-9a-fA-F-]{36})/review$");

    private final AppConfig config;
    private final HttpSupport http;
    private final AuthService authService;
    private final DocumentService documentService;
    private final MatchingService matchingService;
    private final RequestService requestService;

    public ApiServer(AppConfig config, Database database) {
        this.config = config;
        this.http = new HttpSupport(config);
        this.authService = new AuthService(database);
        this.documentService = new DocumentService(database, authService);
        this.matchingService = new MatchingService(database);
        this.requestService = new RequestService(database, authService, matchingService);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(config.serverHost(), config.serverPort()), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (http.handleOptions(exchange)) {
                return;
            }
            String path = http.path(exchange);
            String method = exchange.getRequestMethod().toUpperCase();
            if ("GET".equals(method) && "/api/health".equals(path)) {
                ObjectNode data = Jsons.object();
                data.put("status", "ok");
                data.put("service", "vayomitra");
                http.sendJson(exchange, 200, Jsons.ok(data));
                return;
            }
            if ("GET".equals(method) && "/api/assistance-types".equals(path)) {
                http.sendJson(exchange, 200, Jsons.ok(assistanceTypes()));
                return;
            }
            if ("POST".equals(method) && "/api/auth/elders/register".equals(path)) {
                http.sendJson(exchange, 201, authService.registerElder(http.readJson(exchange)));
                return;
            }
            if ("POST".equals(method) && "/api/auth/volunteers/register".equals(path)) {
                http.sendJson(exchange, 201, authService.registerVolunteer(http.readJson(exchange)));
                return;
            }
            if ("POST".equals(method) && "/api/auth/login".equals(path)) {
                http.sendJson(exchange, 200, authService.login(http.readJson(exchange)));
                return;
            }
            AuthUser user = authenticateIfPresent(exchange, path, method);
            if ("POST".equals(method) && "/api/auth/logout".equals(path)) {
                authService.logout(user);
                ObjectNode data = Jsons.object();
                data.put("message", "Logged out.");
                http.sendJson(exchange, 200, Jsons.ok(data));
                return;
            }
            if (user == null) {
                throw ApiException.unauthorized("Please log in to continue.");
            }
            if ("GET".equals(method) && "/api/auth/me".equals(path)) {
                http.sendJson(exchange, 200, authService.me(user));
                return;
            }
            if ("GET".equals(method) && "/api/volunteers/me/matching-readiness".equals(path)) {
                http.sendJson(exchange, 200, matchingService.volunteerReadiness(user));
                return;
            }
            if ("GET".equals(method) && "/api/volunteers/me/documents".equals(path)) {
                http.sendJson(exchange, 200, documentService.listMine(user));
                return;
            }
            if ("GET".equals(method) && "/api/elders/me/documents".equals(path)) {
                http.sendJson(exchange, 200, documentService.listMine(user));
                return;
            }
            if ("POST".equals(method) && "/api/elders/me/documents".equals(path)) {
                byte[] body = http.readLimited(exchange.getRequestBody(), config.maxUploadBytes() + 4096);
                http.sendJson(exchange, 201, documentService.uploadElder(user, Multipart.parse(http.header(exchange, "Content-Type"), body)));
                return;
            }
            if ("POST".equals(method) && "/api/volunteers/me/documents".equals(path)) {
                byte[] body = http.readLimited(exchange.getRequestBody(), config.maxUploadBytes() + 4096);
                http.sendJson(exchange, 201, documentService.uploadVolunteer(user, Multipart.parse(http.header(exchange, "Content-Type"), body)));
                return;
            }
            Matcher fileMatcher = DOCUMENT_FILE.matcher(path);
            if ("GET".equals(method) && fileMatcher.matches()) {
                DocumentService.FilePayload file = documentService.download(user, UUID.fromString(fileMatcher.group(1)));
                exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + file.filename() + "\"");
                http.sendBytes(exchange, 200, file.contentType(), file.bytes());
                return;
            }
            if ("GET".equals(method) && "/api/admin/documents".equals(path)) {
                http.sendJson(exchange, 200, documentService.adminPending(user));
                return;
            }
            Matcher review = DOCUMENT_REVIEW.matcher(path);
            if ("POST".equals(method) && review.matches()) {
                JsonNode body = http.readJson(exchange);
                http.sendJson(exchange, 200, documentService.review(
                        user,
                        UUID.fromString(review.group(1)),
                        body.path("status").asText(),
                        body.path("notes").asText("")
                ));
                return;
            }
            if ("POST".equals(method) && "/api/requests".equals(path)) {
                http.sendJson(exchange, 201, requestService.create(user, http.readJson(exchange)));
                return;
            }
            if ("GET".equals(method) && "/api/requests".equals(path)) {
                http.sendJson(exchange, 200, requestService.list(user, http.query(exchange).getOrDefault("scope", "")));
                return;
            }
            Matcher requestMatcher = REQUEST_ID.matcher(path);
            if (requestMatcher.matches()) {
                UUID requestId = UUID.fromString(requestMatcher.group(1));
                String rest = requestMatcher.group(2) == null ? "" : requestMatcher.group(2);
                if ("GET".equals(method) && rest.isEmpty()) {
                    http.sendJson(exchange, 200, requestService.get(user, requestId));
                    return;
                }
                if ("POST".equals(method) && "/cancel".equals(rest)) {
                    http.sendJson(exchange, 200, requestService.cancel(user, requestId));
                    return;
                }
                if ("GET".equals(method) && "/matches".equals(rest)) {
                    if (!user.isElder() && !user.isAdmin()) {
                        throw ApiException.forbidden("Only the elder who created this request or an administrator can view its volunteer list.");
                    }
                    requestService.get(user, requestId);
                    http.sendJson(exchange, 200, matchingService.listRecommendations(requestId));
                    return;
                }
                if ("POST".equals(method) && "/matches/refresh".equals(rest)) {
                    http.sendJson(exchange, 200, requestService.refreshMatches(user, requestId));
                    return;
                }
                if ("POST".equals(method) && "/assign".equals(rest)) {
                    JsonNode body = http.readJson(exchange);
                    http.sendJson(exchange, 200, requestService.assign(user, requestId, UUID.fromString(body.path("volunteerId").asText())));
                    return;
                }
                if ("POST".equals(method) && "/apply".equals(rest)) {
                    http.sendJson(exchange, 200, requestService.apply(user, requestId));
                    return;
                }
                if ("POST".equals(method) && "/accept".equals(rest)) {
                    http.sendJson(exchange, 200, requestService.transition(user, requestId, "accept"));
                    return;
                }
                if ("POST".equals(method) && "/start".equals(rest)) {
                    http.sendJson(exchange, 200, requestService.transition(user, requestId, "start"));
                    return;
                }
                if ("POST".equals(method) && "/complete".equals(rest)) {
                    http.sendJson(exchange, 200, requestService.transition(user, requestId, "complete"));
                    return;
                }
            }
            throw ApiException.notFound("No API endpoint matches this path.");
        } catch (ApiException e) {
            http.sendApiException(exchange, e);
        } catch (IllegalArgumentException e) {
            http.sendApiException(exchange, ApiException.badRequest("Invalid identifier in the request."));
        } catch (SQLException e) {
            e.printStackTrace();
            http.sendApiException(exchange, new ApiException(500, "DATABASE_ERROR", "A database error occurred. Please try again."));
        } catch (Exception e) {
            e.printStackTrace();
            http.sendApiException(exchange, new ApiException(500, "SERVER_ERROR", "The server could not complete this request."));
        }
    }

    private AuthUser authenticateIfPresent(HttpExchange exchange, String path, String method) throws SQLException {
        boolean protectedEndpoint = path.startsWith("/api/")
                && !(path.startsWith("/api/auth/elders/register")
                || path.startsWith("/api/auth/volunteers/register")
                || path.equals("/api/auth/login")
                || path.equals("/api/health")
                || path.equals("/api/assistance-types"));
        String token = http.bearerToken(exchange);
        if (token == null) {
            if ("POST".equals(method) && "/api/auth/logout".equals(path)) {
                return null;
            }
            if (protectedEndpoint) {
                throw ApiException.unauthorized("Please log in to continue.");
            }
            return null;
        }
        return authService.authenticate(token);
    }

    private ArrayNode assistanceTypes() {
        ArrayNode items = Jsons.MAPPER.createArrayNode();
        addType(items, "TRANSPORTATION", "Transportation Assistance", "Help elders travel to hospitals, appointments, and other destinations.", true, false, false);
        addType(items, "GROCERY_SUPPORT", "Grocery / Essential / Medicine Shopping", "Help purchase groceries, daily necessities, and medicines.", false, false, false);
        addType(items, "TECHNOLOGY_SUPPORT", "Technology Support", "Help with phones, apps, video calls, and digital services.", false, false, false);
        addType(items, "LONELINESS_SUPPORT", "Companionship / Loneliness Support", "Provide companionship, conversation, and social interaction.", false, false, false);
        addType(items, "BOOKING_SUPPORT", "Booking Support", "Help book appointments, transport, tickets, and other services.", false, false, false);
        addType(items, "HOME_NURSE_ASSISTANCE", "Home Nurse Assistance", "Help arrange appropriate nursing and home healthcare support.", false, true, false);
        addType(items, "EMERGENCY_ASSISTANCE", "Emergency Assistance", "Help elders access appropriate urgent support and emergency services.", false, false, true);
        addType(items, "PET_ASSISTANCE", "Pet Assistance", "Help with pet feeding, walking, and basic pet-care tasks.", false, false, false);
        addType(items, "MINOR_HOME_MAINTENANCE", "Minor Home Maintenance", "Help with small household tasks and minor repairs.", false, false, false);
        return items;
    }

    private void addType(ArrayNode items, String code, String name, String description,
                         boolean requiresDriving, boolean requiresHealthcareCertificate, boolean requiresFirstAidCertificate) {
        ObjectNode item = Jsons.object();
        item.put("code", code);
        item.put("displayName", name);
        item.put("description", description);
        item.put("requiresDrivingLicence", requiresDriving);
        item.put("requiresHealthcareCertificate", requiresHealthcareCertificate);
        item.put("requiresFirstAidCertificate", requiresFirstAidCertificate);
        items.add(item);
    }
}
