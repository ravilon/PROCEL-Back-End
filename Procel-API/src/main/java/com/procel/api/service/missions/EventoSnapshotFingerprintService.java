package com.procel.api.service.missions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

@Component
public class EventoSnapshotFingerprintService {
    private final ObjectMapper objectMapper;

    public EventoSnapshotFingerprintService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writeValueAsString(canonicalJsonNode(node));
        } catch (Exception ex) {
            throw new IllegalArgumentException("contextoSnapshot must be valid JSON", ex);
        }
    }

    public String fingerprint(JsonNode immutableContent) {
        try {
            JsonNode canonical = canonicalJsonNode(immutableContent);
            byte[] bytes = objectMapper.writeValueAsBytes(canonical);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create event occurrence fingerprint", ex);
        }
    }

    public ObjectNode objectNode() {
        return JsonNodeFactory.instance.objectNode();
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
