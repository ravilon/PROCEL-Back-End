package com.procel.ingestion.integration.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.dto.cobalto.CobaltoCompartimentoDTO;
import com.procel.ingestion.integration.cobalto.CobaltoMapper;
import com.procel.ingestion.service.rooms.RoomRecord;
import com.procel.ingestion.service.rooms.RoomsSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class ResourceRoomsSource implements RoomsSource {

    private final ObjectMapper objectMapper;
    private final String resourcePath;

    public ResourceRoomsSource(
            ObjectMapper objectMapper,
            @Value("${procel.rooms.resource-path:seed/cobalto-compartimentos.sample.json}") String resourcePath
    ) {
        this.objectMapper = objectMapper;
        this.resourcePath = resourcePath;
    }

    @Override
    public List<RoomRecord> fetchRooms() {
        try {
            ClassPathResource res = new ClassPathResource(resourcePath);
            try (InputStream in = res.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);

                // aceita tanto: [ ... ] quanto { "rows": [ ... ] }
                JsonNode arr = root.isArray() ? root : root.get("rows");
                if (arr == null || !arr.isArray()) {
                    throw new IllegalStateException("Resource must be an array or an object with 'rows' array");
                }

                List<RoomRecord> out = new ArrayList<>();
                for (JsonNode n : arr) {
                    CobaltoCompartimentoDTO dto = objectMapper.treeToValue(n, CobaltoCompartimentoDTO.class);
                    out.add(CobaltoMapper.toRoom(dto));
                }
                return out;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load rooms from resource: " + resourcePath, e);
        }
    }
}