package com.vayomitra.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class AppConfig {
    private final Map<String, String> values;
    private final Path projectRoot;

    private AppConfig(Map<String, String> values, Path projectRoot) {
        this.values = values;
        this.projectRoot = projectRoot;
    }

    public static AppConfig load(Path projectRoot) throws IOException {
        Map<String, String> values = new HashMap<>();
        Path envFile = projectRoot.resolve("backend").resolve(".env");
        if (Files.isRegularFile(envFile)) {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            values.put(e.getKey(), e.getValue());
        }
        return new AppConfig(values, projectRoot);
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public String dbUrl() {
        return required("DB_URL");
    }

    public String dbUser() {
        return required("DB_USER");
    }

    public String dbPassword() {
        return get("DB_PASSWORD", "");
    }

    public String serverHost() {
        return get("SERVER_HOST", "0.0.0.0");
    }

    public int serverPort() {
        return Integer.parseInt(get("SERVER_PORT", "8080"));
    }

    public String frontendOrigin() {
        return get("FRONTEND_ORIGIN", "http://localhost:5173");
    }

    public int tokenExpiryHours() {
        return Integer.parseInt(get("TOKEN_EXPIRY_HOURS", "24"));
    }

    public int bcryptCost() {
        return Integer.parseInt(get("BCRYPT_COST", "10"));
    }

    public Path uploadDir() {
        Path configured = Path.of(get("UPLOAD_DIR", "uploads"));
        if (configured.isAbsolute()) {
            return configured;
        }
        return projectRoot.resolve("backend").resolve(configured);
    }

    public int maxUploadBytes() {
        return Integer.parseInt(get("MAX_UPLOAD_BYTES", "5242880"));
    }

    public int maxJsonBytes() {
        return Integer.parseInt(get("MAX_JSON_BYTES", "1048576"));
    }

    public double skillWeight() {
        return Double.parseDouble(get("MATCH_SKILL_WEIGHT", "0.40"));
    }

    public double distanceWeight() {
        return Double.parseDouble(get("MATCH_DISTANCE_WEIGHT", "0.25"));
    }

    public double availabilityWeight() {
        return Double.parseDouble(get("MATCH_AVAILABILITY_WEIGHT", "0.20"));
    }

    public double qualificationWeight() {
        return Double.parseDouble(get("MATCH_QUALIFICATION_WEIGHT", "0.15"));
    }

    public double defaultRadiusKm() {
        return Double.parseDouble(get("DEFAULT_SERVICE_RADIUS_KM", "15"));
    }

    public boolean seedDemoData() {
        return Boolean.parseBoolean(get("SEED_DEMO_DATA", "true"));
    }

    public String demoPassword() {
        return get("DEMO_PASSWORD", "Demo@12345");
    }

    public String get(String key, String fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String required(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration: " + key + ". Copy backend/.env.example to backend/.env");
        }
        return value;
    }
}
