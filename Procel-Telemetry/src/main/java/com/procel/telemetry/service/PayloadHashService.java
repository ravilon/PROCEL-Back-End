package com.procel.telemetry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.procel.telemetry.entity.TelemetrySource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PayloadHashService {
    private final ObjectMapper objectMapper;

    public PayloadHashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String fingerprint(
            TelemetrySource source,
            PresenceValue sensorId,
            PresenceValue sourceTimestamp,
            JsonNode payload
    ) {
        try {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            root.put("source", source.name());
            if (sensorId.present()) root.set("sensorId", canonicalJsonNode(sensorId.value()));
            if (sourceTimestamp.present()) root.set("sourceTimestamp", canonicalJsonNode(sourceTimestamp.value()));
            root.set("payload", canonicalJsonNode(payload));

            byte[] canonical = objectMapper.writeValueAsBytes(root);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create payload fingerprint", ex);
        }
    }

    JsonNode canonicalJsonNode(JsonNode node) {
        if (node == null || node.isNull()) return JsonNodeFactory.instance.nullNode();
        if (node.isObject()) {
            ObjectNode object = JsonNodeFactory.instance.objectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                object.set(name, canonicalJsonNode(node.get(name)));
            }
            return object;
        }
        if (node.isArray()) {
            var array = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                array.add(canonicalJsonNode(item));
            }
            return array;
        }
        return node;
    }

    public record PresenceValue(boolean present, JsonNode value) {
        public static PresenceValue absent() {
            return new PresenceValue(false, null);
        }

        public static PresenceValue present(JsonNode value) {
            return new PresenceValue(true, value);
        }
    }
}
