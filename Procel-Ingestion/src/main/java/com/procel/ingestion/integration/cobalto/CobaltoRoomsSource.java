package com.procel.ingestion.integration.cobalto;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.dto.cobalto.CobaltoCompartimentoDTO;
import com.procel.ingestion.service.rooms.RoomRecord;
import com.procel.ingestion.service.rooms.RoomsSource;

@Component
public class CobaltoRoomsSource implements RoomsSource {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CobaltoProperties properties;

    public CobaltoRoomsSource(RestClient.Builder builder,
                             ObjectMapper objectMapper,
                             CobaltoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout((int) properties.getTimeoutMs());
        this.restClient = builder
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<RoomRecord> fetchRooms() {

        final int pageSize = (properties.getPageSize() != null && properties.getPageSize() > 0)
                ? properties.getPageSize()
                : 500;

        int page = 0;
        int totalPages = Integer.MAX_VALUE;
        int totalRecords = -1;

        List<RoomRecord> out = new ArrayList<>();

        // evita loop infinito se o servidor ficar bugando
        int emptyPagesInARow = 0;

        while (page <= totalPages) {

            // retry simples por página
            JsonNode root = null;
            JsonNode rowsNode = null;

            for (int attempt = 1; attempt <= 3; attempt++) {

                String url = UriComponentsBuilder
                        .fromUriString(properties.getUrl())
                        .queryParam("rows", pageSize)
                        .queryParam("page", page)
                        .build(false)
                        .toUriString();

                String body = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(h -> {
                            if (properties.getPhpSessid() != null && !properties.getPhpSessid().isBlank()) {
                                h.add(HttpHeaders.COOKIE, "PHPSESSID=" + properties.getPhpSessid().trim());
                            }
                        })
                        .retrieve()
                        .body(String.class);

                try {
                    root = objectMapper.readTree(body);

                    // 1ª página: captura metadados
                    if (page == 1) {
                        if (root.hasNonNull("records")) totalRecords = root.get("records").asInt();
                        if (root.hasNonNull("total")) totalPages = root.get("total").asInt();

                        // fallback se total não vier ou vier zoado
                        if (totalPages == Integer.MAX_VALUE && totalRecords >= 0) {
                            totalPages = (int) Math.ceil(totalRecords / (double) pageSize);
                        }
                    }

                    rowsNode = root.get("rows");

                    // se rows veio e é array, ok (mesmo vazio)
                    if (rowsNode != null && rowsNode.isArray()) {
                        break;
                    }

                    // rows ausente -> tenta de novo (glitch)
                    rowsNode = null;

                    // backoff leve (sem complicar)
                    try { Thread.sleep(150L * attempt); } catch (InterruptedException ignored) {}

                } catch (Exception parseEx) {
                    // parse falhou: retry
                    rowsNode = null;
                    try { Thread.sleep(150L * attempt); } catch (InterruptedException ignored) {}
                }
            }

            // se depois de retry ainda não veio rows, trata como "página vazia" e segue
            if (rowsNode == null || rowsNode.isEmpty()) {
                emptyPagesInARow++;

                // segurança: se muitas páginas seguidas vierem vazias, aborta com erro explícito
                if (emptyPagesInARow >= 3) {
                    throw new IllegalStateException(
                            "Cobalto returned empty/missing rows for " + emptyPagesInARow +
                            " consecutive pages (last page=" + page + ")."
                    );
                }

                page++;
                continue;
            }

            // page ok, reseta contador de vazias
            emptyPagesInARow = 0;

            for (JsonNode node : rowsNode) {
                try {
                    CobaltoCompartimentoDTO dto = objectMapper.treeToValue(node, CobaltoCompartimentoDTO.class);
                    out.add(CobaltoMapper.toRoom(dto));
                } catch (Exception ignoreBadRow) {
                    // se quiser, loga e segue
                }
            }

            // parada adicional: se já atingiu records
            if (totalRecords >= 0 && out.size() >= totalRecords) {
                break;
            }

            page++;
        }

        return out;
    }
}