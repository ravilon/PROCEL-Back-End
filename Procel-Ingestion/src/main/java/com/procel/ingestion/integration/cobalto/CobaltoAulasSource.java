package com.procel.ingestion.integration.cobalto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.entity.rooms.OcorrenciaAulaTipo;
import com.procel.ingestion.service.rooms.AulaRecord;
import com.procel.ingestion.service.rooms.AulasSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CobaltoAulasSource implements AulasSource {

    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(\\d{2}:\\d{2})\\s*/\\s*(\\d{2}:\\d{2})"
    );
    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "<a\\b[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern DISCIPLINE_ID_PATTERN = Pattern.compile(
            "/disciplinas/id/(\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CLASS_TEXT_PATTERN = Pattern.compile(
            "^\\(([^)]+)\\)\\s+(.+?)\\s+-\\s+(.+)$"
    );

    private static final List<DayColumn> DAY_COLUMNS = List.of(
            new DayColumn("domingo", DayOfWeek.SUNDAY),
            new DayColumn("segunda", DayOfWeek.MONDAY),
            new DayColumn("terca", DayOfWeek.TUESDAY),
            new DayColumn("quarta", DayOfWeek.WEDNESDAY),
            new DayColumn("quinta", DayOfWeek.THURSDAY),
            new DayColumn("sexta", DayOfWeek.FRIDAY),
            new DayColumn("sabado", DayOfWeek.SATURDAY)
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CobaltoProperties properties;

    public CobaltoAulasSource(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            CobaltoProperties properties
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restClient = builder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<AulaRecord> fetchAulas(String compartimentoId, LocalDate weekStart) {
        if (properties.getScheduleUrl() == null || properties.getScheduleUrl().isBlank()) {
            throw new IllegalStateException("Cobalto schedule URL is not configured");
        }

        List<AulaRecord> records = new ArrayList<>();
        for (int turno = 1; turno <= 3; turno++) {
            JsonNode root = fetchTurno(compartimentoId, weekStart, turno);
            JsonNode rows = root.get("rows");

            if (rows == null || !rows.isArray()) {
                throw new IllegalStateException(
                        "Cobalto schedule response has no rows array for room=" +
                                compartimentoId + ", turno=" + turno
                );
            }
            if (rows.isEmpty()) {
                throw new IllegalStateException(
                        "Cobalto schedule response has an empty rows array for room=" +
                                compartimentoId + ", turno=" + turno
                );
            }

            for (JsonNode row : rows) {
                parseRow(row, weekStart, turno, records);
            }
        }
        return records;
    }

    private JsonNode fetchTurno(String compartimentoId, LocalDate weekStart, int turno) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String url = UriComponentsBuilder
                        .fromUriString(properties.getScheduleUrl())
                        .queryParam("turno", turno)
                        .queryParam("dataDaSemana", API_DATE.format(weekStart))
                        .queryParam("compartimentoId", compartimentoId)
                        .queryParam("_search", false)
                        .queryParam("rows", -1)
                        .queryParam("page", 1)
                        .queryParam("sord", "ASC")
                        .queryParam("apenasSalasDeAula", "S")
                        .build()
                        .encode()
                        .toUriString();

                String body = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers -> addSessionCookie(headers, properties.getPhpSessid()))
                        .retrieve()
                        .body(String.class);

                return objectMapper.readTree(body);
            } catch (Exception ex) {
                lastFailure = new IllegalStateException(
                        "Failed to fetch schedule for room=" + compartimentoId +
                                ", turno=" + turno + ", attempt=" + attempt,
                        ex
                );
                sleepBeforeRetry(attempt);
            }
        }

        throw lastFailure;
    }

    void parseRow(
            JsonNode row,
            LocalDate weekStart,
            int turno,
            List<AulaRecord> output
    ) {
        if (!row.hasNonNull("periodo") || !row.hasNonNull("periodo_descritivo")) {
            return;
        }

        int periodo = row.get("periodo").asInt();
        Matcher timeMatcher = TIME_PATTERN.matcher(row.get("periodo_descritivo").asText());
        if (!timeMatcher.find()) {
            return;
        }

        LocalTime horaInicio = LocalTime.parse(timeMatcher.group(1));
        LocalTime horaFim = LocalTime.parse(timeMatcher.group(2));

        for (DayColumn dayColumn : DAY_COLUMNS) {
            JsonNode cell = row.get(dayColumn.jsonField());
            if (cell == null || cell.isNull() || cell.asText().isBlank()) {
                continue;
            }

            LocalDate date = dateFor(weekStart, dayColumn.dayOfWeek());
            parseCell(
                    cell.asText(),
                    date,
                    turno,
                    periodo,
                    horaInicio,
                    horaFim,
                    output
            );
        }
    }

    private void parseCell(
            String html,
            LocalDate date,
            int turno,
            int periodo,
            LocalTime horaInicio,
            LocalTime horaFim,
            List<AulaRecord> output
    ) {
        Matcher anchorMatcher = ANCHOR_PATTERN.matcher(html);
        boolean foundAnchor = false;

        while (anchorMatcher.find()) {
            foundAnchor = true;
            String href = HtmlUtils.htmlUnescape(anchorMatcher.group(1));
            String description = plainText(anchorMatcher.group(2));
            if (!description.isBlank()) {
                output.add(toRecord(
                        href,
                        description,
                        date,
                        turno,
                        periodo,
                        horaInicio,
                        horaFim
                ));
            }
        }

        if (!foundAnchor) {
            String description = plainText(html);
            if (!description.isBlank()) {
                output.add(toRecord(
                        null,
                        description,
                        date,
                        turno,
                        periodo,
                        horaInicio,
                        horaFim
                ));
            }
        }
    }

    private AulaRecord toRecord(
            String href,
            String description,
            LocalDate date,
            int turno,
            int periodo,
            LocalTime horaInicio,
            LocalTime horaFim
    ) {
        Long disciplinaId = disciplineId(href);
        ParsedClassText parsedText = parseClassText(description);
        OcorrenciaAulaTipo tipo = classify(disciplinaId, description);

        return new AulaRecord(
                disciplinaId,
                disciplinaId != null ? parsedText.disciplineName() : null,
                disciplinaId != null ? parsedText.unitAbbreviation() : null,
                date,
                turno,
                periodo,
                horaInicio,
                horaFim,
                disciplinaId != null ? parsedText.classGroup() : null,
                tipo,
                description
        );
    }

    private Long disciplineId(String href) {
        if (href == null) return null;
        Matcher matcher = DISCIPLINE_ID_PATTERN.matcher(href);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private ParsedClassText parseClassText(String description) {
        Matcher matcher = CLASS_TEXT_PATTERN.matcher(description);
        if (!matcher.matches()) {
            return new ParsedClassText(null, null, description);
        }
        return new ParsedClassText(
                matcher.group(1).trim(),
                matcher.group(2).trim(),
                matcher.group(3).trim()
        );
    }

    private OcorrenciaAulaTipo classify(Long disciplinaId, String description) {
        if (disciplinaId != null) return OcorrenciaAulaTipo.AULA;

        String normalized = description.toLowerCase(Locale.ROOT);
        if (normalized.contains("prova")) return OcorrenciaAulaTipo.PROVA;
        if (normalized.contains("reserva") || normalized.contains("sala")) {
            return OcorrenciaAulaTipo.RESERVA;
        }
        return OcorrenciaAulaTipo.OUTRO;
    }

    private String plainText(String html) {
        String withoutTags = html.replaceAll("(?s)<[^>]+>", " ");
        return HtmlUtils.htmlUnescape(withoutTags)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private LocalDate dateFor(LocalDate weekStart, DayOfWeek dayOfWeek) {
        int offset = (dayOfWeek.getValue() % 7);
        return weekStart.plusDays(offset);
    }

    private void addSessionCookie(HttpHeaders headers, String phpSessid) {
        if (phpSessid != null && !phpSessid.isBlank()) {
            headers.add(HttpHeaders.COOKIE, "PHPSESSID=" + phpSessid.trim());
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(150L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Cobalto schedule request", ex);
        }
    }

    private record DayColumn(String jsonField, DayOfWeek dayOfWeek) {}

    private record ParsedClassText(
            String unitAbbreviation,
            String classGroup,
            String disciplineName
    ) {}
}
