package com.procel.api.service.sensors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.procel.api.dto.sensors.SensorIngestDTOs;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class PayloadFingerprintService {
    private final ObjectMapper objectMapper;

    public PayloadFingerprintService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String fingerprint(SensorIngestDTOs.CanonicalIngestRequest request) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(canonicalNode(request));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create payload fingerprint", ex);
        }
    }

    JsonNode canonicalNode(SensorIngestDTOs.CanonicalIngestRequest request) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("messageId", request.messageId());
        root.put("sensorExternalId", request.sensorExternalId());
        root.put("source", request.source().name());
        root.put("timestamp", Instant.from(request.timestamp()).toString());
        root.set("values", canonicalObject(request.values()));
        return root;
    }

    private ObjectNode canonicalObject(Map<String, Object> values) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, Object> entry : new TreeMap<>(values).entrySet()) {
            node.set(entry.getKey(), canonicalValue(entry.getValue()));
        }
        return node;
    }

    private JsonNode canonicalValue(Object value) {
        if (value == null) {
            return NullNode.instance;
        }
        if (value instanceof JsonNode jsonNode) {
            return canonicalJsonNode(jsonNode);
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                node.set(entry.getKey(), canonicalValue(entry.getValue()));
            }
            return node;
        }
        if (value instanceof Collection<?> collection) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            for (Object item : collection) {
                array.add(canonicalValue(item));
            }
            return array;
        }
        if (value instanceof Boolean bool) {
            return BooleanNode.valueOf(bool);
        }
        if (value instanceof BigDecimal decimal) {
            return DecimalNode.valueOf(normalize(decimal));
        }
        if (value instanceof BigInteger integer) {
            return DecimalNode.valueOf(new BigDecimal(integer));
        }
        if (value instanceof Number number) {
            return DecimalNode.valueOf(normalize(new BigDecimal(number.toString())));
        }
        if (value instanceof CharSequence text) {
            return TextNode.valueOf(text.toString());
        }
        return TextNode.valueOf(value.toString());
    }

    private JsonNode canonicalJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.instance;
        }
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
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                array.add(canonicalJsonNode(item));
            }
            return array;
        }
        if (node.isNumber()) {
            return DecimalNode.valueOf(normalize(node.decimalValue()));
        }
        if (node.isBoolean()) {
            return BooleanNode.valueOf(node.booleanValue());
        }
        if (node.isTextual()) {
            return TextNode.valueOf(node.textValue());
        }
        return TextNode.valueOf(node.toString());
    }

    private static BigDecimal normalize(BigDecimal decimal) {
        BigDecimal normalized = decimal.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }
}
