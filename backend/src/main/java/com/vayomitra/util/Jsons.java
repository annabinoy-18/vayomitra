package com.vayomitra.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Jsons {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Jsons() {
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ObjectNode ok(JsonNode data) {
        ObjectNode root = object();
        root.put("ok", true);
        root.set("data", data);
        return root;
    }

    public static ObjectNode error(String code, String message, JsonNode fields) {
        ObjectNode root = object();
        root.put("ok", false);
        ObjectNode error = object();
        error.put("code", code);
        error.put("message", message);
        if (fields != null) {
            error.set("fields", fields);
        }
        root.set("error", error);
        return root;
    }
}
