package com.vayomitra.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Validation {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{10}$");
    private static final Pattern PIN = Pattern.compile("^[0-9]{6}$");
    private static final List<String> GENDERS = List.of("MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY");
    public static final List<String> ASSISTANCE_TYPES = List.of(
            "TRANSPORTATION", "GROCERY_SUPPORT", "TECHNOLOGY_SUPPORT", "LONELINESS_SUPPORT",
            "BOOKING_SUPPORT", "HOME_NURSE_ASSISTANCE", "PET_ASSISTANCE", "MINOR_HOME_MAINTENANCE",
            "EMERGENCY_ASSISTANCE"
    );

    private final Map<String, String> errors = new LinkedHashMap<>();

    public static String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return "";
        }
        return node.path(field).asText("").trim();
    }

    public static Double optionalDouble(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull() || node.path(field).asText("").isBlank()) {
            return null;
        }
        if (node.path(field).isNumber()) {
            return node.path(field).asDouble();
        }
        try {
            return Double.parseDouble(node.path(field).asText());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public void require(String field, String value, int min, int max) {
        if (value == null || value.isBlank()) {
            errors.put(field, "This field is required.");
        } else if (value.length() < min || value.length() > max) {
            errors.put(field, "Must be between " + min + " and " + max + " characters.");
        }
    }

    public void email(String field, String value) {
        if (value == null || !EMAIL.matcher(value).matches()) {
            errors.put(field, "Enter a valid email address.");
        }
    }

    public void phone(String field, String value) {
        if (value == null || !PHONE.matcher(value).matches()) {
            errors.put(field, "Enter a 10-digit phone number.");
        }
    }

    public void pin(String field, String value) {
        if (value == null || !PIN.matcher(value).matches()) {
            errors.put(field, "Enter a 6-digit PIN code.");
        }
    }

    public void gender(String field, String value) {
        if (!GENDERS.contains(value)) {
            errors.put(field, "Choose a valid gender option.");
        }
    }

    public void password(String field, String value) {
        if (value == null || value.length() < 8) {
            errors.put(field, "Password must be at least 8 characters.");
        } else if (!value.matches(".*[A-Za-z].*") || !value.matches(".*[0-9].*")) {
            errors.put(field, "Password must include letters and numbers.");
        }
    }

    public void confirmPassword(String password, String confirm) {
        if (password != null && !password.equals(confirm)) {
            errors.put("confirmPassword", "Passwords do not match.");
        }
    }

    public LocalDate dateOfBirth(String field, String value, int minAge, int maxAge) {
        try {
            LocalDate date = LocalDate.parse(value);
            int age = LocalDate.now().getYear() - date.getYear()
                    - (LocalDate.now().getDayOfYear() < date.getDayOfYear() ? 1 : 0);
            if (date.isAfter(LocalDate.now())) {
                errors.put(field, "Date of birth cannot be in the future.");
            } else if (age < minAge || age > maxAge) {
                errors.put(field, "Age must be between " + minAge + " and " + maxAge + ".");
            }
            return date;
        } catch (DateTimeParseException e) {
            errors.put(field, "Enter a valid date of birth (YYYY-MM-DD).");
            return null;
        }
    }

    public LocalDate date(String field, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.put(field, "Enter a valid date (YYYY-MM-DD).");
            return null;
        }
    }

    public LocalTime time(String field, String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            errors.put(field, "Enter a valid time (HH:MM).");
            return null;
        }
    }

    public void coordinates(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return;
        }
        if (latitude == null || longitude == null || latitude.isNaN() || longitude.isNaN() || !Geo.valid(latitude, longitude)) {
            errors.put("location", "Latitude must be between -90 and 90, longitude between -180 and 180.");
        }
    }

    public void assistanceType(String field, String value) {
        if (!ASSISTANCE_TYPES.contains(value)) {
            errors.put(field, "Choose one of the nine supported assistance types.");
        }
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw ApiException.validation("Please correct the highlighted fields.", errors);
        }
    }

    public Map<String, String> errors() {
        return errors;
    }

    public static List<String> stringList(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        JsonNode array = node.path(field);
        if (array.isArray()) {
            array.forEach(item -> values.add(item.asText().trim()));
        }
        return values;
    }
}
