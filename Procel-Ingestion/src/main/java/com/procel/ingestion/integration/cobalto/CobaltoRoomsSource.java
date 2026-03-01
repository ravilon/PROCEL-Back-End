package com.procel.ingestion.integration.cobalto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.dto.cobalto.CobaltoCompartimentoDTO;
import com.procel.ingestion.service.rooms.RoomRecord;
import com.procel.ingestion.service.rooms.RoomsSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class CobaltoRoomsSource implements RoomsSource {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final CobaltoProperties properties;

    public CobaltoRoomsSource(ObjectMapper objectMapper, CobaltoProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;

        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<RoomRecord> fetchRooms() {
        String uri = UriComponentsBuilder
                .fromUriString(properties.getUrl())
                .queryParam("rows", -1)
                .queryParam("_search", "false")
                .queryParam("sidx", "compartimento_id, compartimento_nome")
                .queryParam("sord", "asc")
                .toUriString();

        String body = client.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    String sess = properties.getPhpSessid();
                    if (sess != null && !sess.isBlank()) {
                        h.add(HttpHeaders.COOKIE, "PHPSESSID=" + sess.trim());
                    }
                })
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode rows = root.get("rows");

            if (rows == null || !rows.isArray()) {
                throw new IllegalStateException("Invalid Cobalto response: missing 'rows'");
            }

            List<RoomRecord> result = new ArrayList<>();
            for (JsonNode node : rows) {
                CobaltoCompartimentoDTO dto = objectMapper.treeToValue(node, CobaltoCompartimentoDTO.class);
                result.add(CobaltoMapper.toRoom(dto));
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Error parsing Cobalto response", e);
        }
    }
}