package com.vayomitra.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.vayomitra.config.AppConfig;
import com.vayomitra.util.ApiException;
import com.vayomitra.util.Jsons;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class HttpSupport {
    private final AppConfig config;

    public HttpSupport(AppConfig config) {
        this.config = config;
    }

    public void applyCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", config.frontendOrigin());
        headers.set("Vary", "Origin");
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Allow-Credentials", "true");
        headers.set("Access-Control-Max-Age", "600");
    }

    public boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            applyCors(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    public JsonNode readJson(HttpExchange exchange) throws IOException {
        String contentType = header(exchange, "Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "JSON endpoints require Content-Type: application/json.");
        }
        byte[] body = readLimited(exchange.getRequestBody(), config.maxJsonBytes());
        if (body.length == 0) {
            throw ApiException.badRequest("Request body is required.");
        }
        try {
            return Jsons.MAPPER.readTree(body);
        } catch (Exception e) {
            throw ApiException.badRequest("Request body is not valid JSON.");
        }
    }

    public byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new ApiException(413, "PAYLOAD_TOO_LARGE", "Request is larger than the allowed limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public void sendJson(HttpExchange exchange, int status, ObjectNode body) throws IOException {
        applyCors(exchange);
        byte[] bytes = Jsons.MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        applyCors(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void sendApiException(HttpExchange exchange, ApiException error) throws IOException {
        ObjectNode fields = null;
        if (error.fields() != null && !error.fields().isEmpty()) {
            fields = Jsons.object();
            error.fields().forEach(fields::put);
        }
        sendJson(exchange, error.status(), Jsons.error(error.code(), error.getMessage(), fields));
    }

    public String bearerToken(HttpExchange exchange) {
        String header = header(exchange, "Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    public String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public Map<String, String> query(HttpExchange exchange) {
        Map<String, String> map = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                map.put(urlDecode(part), "");
            } else {
                map.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
            }
        }
        return map;
    }

    public String path(HttpExchange exchange) {
        return exchange.getRequestURI().getPath();
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
