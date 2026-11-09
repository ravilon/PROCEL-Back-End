package com.proben.api.service.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proben.api.dto.shared.SharedContextDTOs.*;
import com.proben.api.exception.ConflictException;
import com.proben.api.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class SharedContextService {
    private static final String EXTERNAL_TEMPERATURE = "temperatura_externa";
    private static final BigDecimal MIN_COVERED_HOURS = new BigDecimal("120");
    private final JdbcTemplate jdbc;
    private final CredentialEncryptionService credentials;
    private final ObjectMapper json = new ObjectMapper();

    public SharedContextService(JdbcTemplate jdbc, CredentialEncryptionService credentials) { this.jdbc = jdbc; this.credentials = credentials; }

    @Transactional
    public ContextResponse create(ContextRequest request) {
        validateContext(request);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into contexto_compartilhado(id,nome,descricao,ativo,location_mode,campus_nome,latitude,longitude,timezone)
            values (?,?,?,?,?,?,?,?,?)
            """, id, request.nome().trim(), blank(request.descricao()), request.ativo() == null || request.ativo(),
                locationMode(request.locationMode()), campus(request), ownLatitude(request), ownLongitude(request), zone(request.timezone()));
        return get(id);
    }

    @Transactional
    public ContextResponse update(UUID id, ContextRequest request) {
        validateContext(request);
        int changed = jdbc.update("""
            update contexto_compartilhado set nome=?,descricao=?,ativo=?,location_mode=?,campus_nome=?,latitude=?,longitude=?,timezone=?,updated_at=now()
            where id=?
            """, request.nome().trim(), blank(request.descricao()), request.ativo() == null || request.ativo(),
                locationMode(request.locationMode()), campus(request), ownLatitude(request), ownLongitude(request), zone(request.timezone()), id);
        if (changed == 0) throw new NotFoundException("Contexto compartilhado not found id=" + id);
        return get(id);
    }

    public List<ContextResponse> list() {
        return jdbc.query("select id from contexto_compartilhado order by nome", (rs, n) -> get(rs.getObject(1, UUID.class)));
    }

    public ContextDeletePreview deletePreview(UUID id) {
        ContextResponse context = get(id);
        int rooms = count("select count(*) from contexto_compartilhado_compartimento where contexto_id=?", id);
        int sensors = count("select count(*) from sensor where contexto_compartilhado_id=?", id);
        int integrations = count("select count(*) from contexto_integracao_externa where contexto_id=?", id);
        int sources = count("select count(*) from contexto_fonte_selecao fs join sensor s on s.external_id=fs.sensor_external_id where s.contexto_compartilhado_id=?", id);
        int measurements = count("select count(*) from medicao m join sensor s on s.external_id=m.sensor_external_id where s.contexto_compartilhado_id=?", id);
        int audits = count("select count(*) from contexto_coleta_execucao ce join contexto_integracao_externa i on i.id=ce.integracao_id where i.contexto_id=?", id);
        List<String> dependencies = new ArrayList<>();
        if (sensors > 0) dependencies.add("sensores: " + sensors);
        if (integrations > 0) dependencies.add("integrações: " + integrations);
        if (sources > 0) dependencies.add("fontes selecionadas: " + sources);
        if (measurements > 0) dependencies.add("medições: " + measurements);
        if (audits > 0) dependencies.add("auditorias: " + audits);
        return new ContextDeletePreview(id, context.nome(), rooms, sensors, integrations, sources,
                measurements, audits, dependencies.isEmpty(), List.copyOf(dependencies));
    }

    @Transactional
    public void delete(UUID id) {
        ContextDeletePreview preview = deletePreview(id);
        if (!preview.deletable()) throw new ConflictException("Contexto possui dependências: " + String.join(", ", preview.dependencies()));
        jdbc.update("delete from contexto_compartilhado_compartimento where contexto_id=?", id);
        if (jdbc.update("delete from contexto_compartilhado where id=?", id) != 1)
            throw new NotFoundException("Contexto compartilhado not found id=" + id);
    }

    public ContextResponse get(UUID id) {
        var rows = jdbc.query("""
                select c.*,case when c.location_mode='CAMPUS' then cp.latitude else c.latitude end resolved_latitude,
                  case when c.location_mode='CAMPUS' then cp.longitude else c.longitude end resolved_longitude
                from contexto_compartilhado c left join campus cp on cp.nome=c.campus_nome where c.id=?
                """, (rs, n) -> new ContextResponse(
                rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("descricao"), rs.getBoolean("ativo"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("timezone"),
                rs.getString("location_mode"),rs.getString("campus_nome"),rs.getBigDecimal("resolved_latitude"),
                rs.getBigDecimal("resolved_longitude"),rooms(id)), id);
        if (rows.isEmpty()) throw new NotFoundException("Contexto compartilhado not found id=" + id);
        return rows.getFirst();
    }

    @Transactional
    public ContextResponse replaceRooms(UUID id, RoomsRequest request) {
        get(id);
        List<String> ids = request == null || request.compartimentoIds() == null ? List.of() : request.compartimentoIds().stream()
                .filter(Objects::nonNull).map(s -> s == null ? null : s.trim()).filter(s -> !s.isBlank()).distinct().toList();
        for (String room : ids) if (jdbc.queryForObject("select count(*) from compartimento where id=?", Integer.class, room) == 0)
            throw new NotFoundException("Compartimento not found id=" + room);
        jdbc.update("delete from contexto_compartilhado_compartimento where contexto_id=?", id);
        ids.forEach(room -> jdbc.update("insert into contexto_compartilhado_compartimento(contexto_id,compartimento_id) values (?,?)", id, room));
        return get(id);
    }

    @Transactional
    public void selectSource(String roomId, SourceSelectionRequest request) {
        if (request == null || request.parametroDefId() == null || request.sensorExternalId() == null) throw new IllegalArgumentException("parameter and sensor are required");
        Integer eligible = jdbc.queryForObject("""
            select count(*) from sensor s
            join contexto_compartilhado c on c.id=s.contexto_compartilhado_id and c.ativo
            join contexto_compartilhado_compartimento cc on cc.contexto_id=c.id and cc.ativo
            join parametro_def p on p.id=? and p.tipo_nome=s.tipo_nome and p.ativo
            where cc.compartimento_id=? and s.external_id=? and s.ativo and p.nome=? and p.data_type='NUMERIC'
            """, Integer.class, request.parametroDefId(), roomId, request.sensorExternalId().trim(), EXTERNAL_TEMPERATURE);
        if (eligible == null || eligible != 1) throw new IllegalArgumentException("source must be an active temperatura_externa sensor covering the room");
        int validity = request.validadeSegundos() == null ? 3600 : request.validadeSegundos();
        if (validity <= 0) throw new IllegalArgumentException("validadeSegundos must be positive");
        jdbc.update("""
            insert into contexto_fonte_selecao(compartimento_id,parametro_def_id,sensor_external_id,validade_segundos)
            values (?,?,?,?) on conflict(compartimento_id,parametro_def_id) do update
            set sensor_external_id=excluded.sensor_external_id, validade_segundos=excluded.validade_segundos, updated_at=now()
            """, roomId, request.parametroDefId(), request.sensorExternalId().trim(), validity);
    }

    @Transactional
    public void selectSourceBatch(BatchSourceSelectionRequest request) {
        if(request==null||request.compartimentoIds()==null||request.compartimentoIds().isEmpty())throw new IllegalArgumentException("compartimentoIds are required");
        for(String room:request.compartimentoIds().stream().filter(Objects::nonNull).map(s -> s == null ? null : s.trim()).filter(s -> s != null && !s.isBlank()).distinct().toList())
            selectSource(room,new SourceSelectionRequest(request.parametroDefId(),request.sensorExternalId(),request.validadeSegundos()));
    }

    @Transactional
    public IntegrationResponse configureIntegration(UUID contextId, IntegrationRequest r) {
        get(contextId);
        validateIntegration(r);
        Integer sensorOk = jdbc.queryForObject("select count(*) from sensor where external_id=? and contexto_compartilhado_id=?",
                Integer.class, r.sensorExternalId().trim(), contextId);
        if (sensorOk == null || sensorOk != 1) throw new IllegalArgumentException("sensor must belong to context");
        UUID id = jdbc.query("select id from contexto_integracao_externa where sensor_external_id=?", (rs,n)->rs.getObject(1,UUID.class), r.sensorExternalId().trim())
                .stream().findFirst().orElse(UUID.randomUUID());
        if (r.credential() != null && r.credential().isBlank()) throw new IllegalArgumentException("credential must be omitted to preserve it; use the remove action to remove it");
        boolean managedExists = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from contexto_integracao_externa where id=? and credential_ciphertext is not null)", Boolean.class, id));
        String auth = authMode(r.authMode());
        if (!"NONE".equals(auth) && r.credential() == null && !managedExists)
            throw new IllegalArgumentException("authentication requires a credential");
        jdbc.update("""
            insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,descricao,provider_name,attribution,ativo,endpoint_url,metodo,content_type,request_body,
              auth_mode,credential_header,credential_query_param,coordinate_query_param,query_params_json,
              headers_json,mappings_json,temperature_json_pointer,observed_at_json_pointer,observed_at_format,timestamp_timezone,source_id_json_pointer,
              interval_seconds,timeout_millis,max_attempts,retry_delay_millis,validity_seconds,automatic_collection,next_run_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())
            on conflict(sensor_external_id) do update set nome=excluded.nome,descricao=excluded.descricao,
              provider_name=excluded.provider_name,attribution=excluded.attribution,ativo=excluded.ativo,endpoint_url=excluded.endpoint_url,
              metodo=excluded.metodo,content_type=excluded.content_type,request_body=excluded.request_body,auth_mode=excluded.auth_mode,
              credential_header=excluded.credential_header,credential_query_param=excluded.credential_query_param,
              coordinate_query_param=excluded.coordinate_query_param,query_params_json=excluded.query_params_json,
              headers_json=excluded.headers_json,mappings_json=excluded.mappings_json,
              temperature_json_pointer=excluded.temperature_json_pointer,observed_at_json_pointer=excluded.observed_at_json_pointer,
              observed_at_format=excluded.observed_at_format,timestamp_timezone=excluded.timestamp_timezone,source_id_json_pointer=excluded.source_id_json_pointer,
              interval_seconds=excluded.interval_seconds,timeout_millis=excluded.timeout_millis,max_attempts=excluded.max_attempts,
              retry_delay_millis=excluded.retry_delay_millis,validity_seconds=excluded.validity_seconds,
              automatic_collection=excluded.automatic_collection,
              next_run_at=least(contexto_integracao_externa.next_run_at,now()),updated_at=now()
            """, id, contextId, r.sensorExternalId().trim(), r.nome().trim(), blank(r.descricao()), blank(r.providerName()),
                blank(r.attribution()), r.ativo()!=null&&r.ativo(), r.endpointUrl().trim(), method(r.metodo()),
                blank(r.contentType())==null?"application/json":r.contentType().trim(), blank(r.requestBody()), auth, blank(r.credentialHeader()),
                blank(r.credentialQueryParam()),blank(r.coordinateQueryParam()),blank(r.queryParamsJson()),
                jsonText(r.headersJson(), "[]"), mappingsJson(r), blank(r.temperatureJsonPointer()),
                blank(r.observedAtJsonPointer()), observedFormat(r.observedAtFormat()), blank(r.observedAtTimezone()), blank(r.sourceIdJsonPointer()),
                nvl(r.intervalSeconds(),900), nvl(r.timeoutMillis(),5000), nvl(r.maxAttempts(),2),
                nvl(r.retryDelayMillis(),500),nvl(r.validitySeconds(),3600),r.automaticCollection()!=null&&r.automaticCollection());
        if (r.credential() != null) {
            var encrypted = credentials.encrypt(id, r.credential());
            jdbc.update("update contexto_integracao_externa set credential_ciphertext=?,credential_iv=?,updated_at=now() where id=?",
                    encrypted.ciphertext(), encrypted.iv(), id);
        }
        return integration(id);
    }

    @Transactional
    public IntegrationResponse removeManagedCredential(UUID id) {
        if (jdbc.update("update contexto_integracao_externa set credential_ciphertext=null,credential_iv=null,updated_at=now() where id=?", id) == 0)
            throw new NotFoundException("Integration not found id=" + id);
        return integration(id);
    }

    public List<IntegrationResponse> integrations(UUID contextId) {
        return jdbc.query("select id from contexto_integracao_externa where contexto_id=? order by nome",
                (rs,n)->integration(rs.getObject(1,UUID.class)), contextId);
    }

    @Transactional
    public IntegrationResponse setIntegrationActive(UUID id, boolean active) {
        if(jdbc.update("update contexto_integracao_externa set ativo=?,next_run_at=case when ? then now() else next_run_at end,locked_until=null,locked_by=null,updated_at=now() where id=?",active,active,id)==0)
            throw new NotFoundException("Integration not found id="+id);
        return integration(id);
    }

    public ObservationResponse latest(String roomId, UUID parameterId, Instant at) {
        Instant reference = at == null ? Instant.now() : at;
        var rows = jdbc.query("""
            select pv.numeric_value,p.numeric_unit,m.timestamp,s.external_id,m.source,fs.validade_segundos,ce.location_hash
            from contexto_fonte_selecao fs join sensor s on s.external_id=fs.sensor_external_id
            join medicao m on m.sensor_external_id=s.external_id
            join parametro_valor pv on pv.medicao_id=m.id and pv.parametro_def_id=fs.parametro_def_id
            join parametro_def p on p.id=pv.parametro_def_id
            join contexto_integracao_externa i on i.sensor_external_id=s.external_id
            join contexto_coleta_execucao ce on ce.integracao_id=i.id and ce.medicao_id=m.id
            join contexto_compartilhado c on c.id=i.contexto_id
            join contexto_compartilhado_compartimento cc on cc.contexto_id=c.id and cc.compartimento_id=fs.compartimento_id
            left join campus cp on cp.nome=c.campus_nome
            where fs.compartimento_id=? and fs.parametro_def_id=? and m.timestamp<=?
              and c.ativo and cc.ativo and s.ativo and p.ativo and i.ativo
              and ce.location_hash=encode(digest(concat_ws('|',case when c.location_mode='CAMPUS' then cp.latitude else c.latitude end,
                case when c.location_mode='CAMPUS' then cp.longitude else c.longitude end),'sha256'),'hex')
            order by m.timestamp desc,m.id desc limit 1
            """, (rs,n) -> {
                Instant observed=rs.getTimestamp(3).toInstant(); Instant valid=observed.plusSeconds(rs.getInt(6));
                return new ObservationResponse(valid.isBefore(reference)?"STALE":"AVAILABLE",rs.getBigDecimal(1),rs.getString(2),observed,
                        rs.getString(4),rs.getString(5),valid,rs.getString(7));
            }, roomId, parameterId, Timestamp.from(reference));
        return rows.isEmpty() ? new ObservationResponse("MISSING",null,null,null,null,null,null,null) : rows.getFirst();
    }

    public List<RoomObservationResponse> roomObservations(String roomId, Instant at) {
        record Selection(UUID contextId, String contextName, UUID parameterId, String parameterName,
                         String parameterUnit) {}
        List<Selection> selections = jdbc.query("""
            select distinct c.id,c.nome,p.id,p.nome,p.numeric_unit
            from contexto_fonte_selecao fs
            join parametro_def p on p.id=fs.parametro_def_id
            join sensor s on s.external_id=fs.sensor_external_id
            join contexto_compartilhado c on c.id=s.contexto_compartilhado_id
            join contexto_compartilhado_compartimento cc
              on cc.contexto_id=c.id and cc.compartimento_id=fs.compartimento_id
            where fs.compartimento_id=?
            order by c.nome,p.nome
            """, (rs,n) -> new Selection(rs.getObject(1,UUID.class),rs.getString(2),
                rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5)), roomId);
        return selections.stream().map(selection -> new RoomObservationResponse(
                selection.contextId(), selection.contextName(), selection.parameterId(),
                selection.parameterName(), selection.parameterUnit(),
                latest(roomId, selection.parameterId(), at))).toList();
    }

    public SevenDayAverageResponse average7d(String roomId, UUID parameterId, Instant at) {
        Instant end=at==null?Instant.now():at; Instant start=end.minus(Duration.ofDays(7));
        String timezone=jdbc.query("""
            select c.timezone from contexto_fonte_selecao fs join sensor s on s.external_id=fs.sensor_external_id
            join contexto_compartilhado c on c.id=s.contexto_compartilhado_id where fs.compartimento_id=? and fs.parametro_def_id=?
            """, (rs,n)->rs.getString(1), roomId,parameterId).stream().findFirst().orElse("America/Sao_Paulo");
        var points=jdbc.query("""
            select distinct on (m.timestamp) m.timestamp,pv.numeric_value
            from contexto_fonte_selecao fs join medicao m on m.sensor_external_id=fs.sensor_external_id
            join parametro_valor pv on pv.medicao_id=m.id and pv.parametro_def_id=fs.parametro_def_id
            join contexto_integracao_externa i on i.sensor_external_id=fs.sensor_external_id
            join contexto_coleta_execucao ce on ce.integracao_id=i.id and ce.medicao_id=m.id
            join contexto_compartilhado c on c.id=i.contexto_id
            join contexto_compartilhado_compartimento cc on cc.contexto_id=c.id and cc.compartimento_id=fs.compartimento_id
            join parametro_def p on p.id=fs.parametro_def_id
            left join campus cp on cp.nome=c.campus_nome
            where fs.compartimento_id=? and fs.parametro_def_id=? and m.timestamp between ? and ? and pv.numeric_value is not null
              and c.ativo and cc.ativo and fs.sensor_external_id in (select external_id from sensor where ativo)
              and p.ativo and i.ativo
              and ce.location_hash=encode(digest(concat_ws('|',case when c.location_mode='CAMPUS' then cp.latitude else c.latitude end,
                case when c.location_mode='CAMPUS' then cp.longitude else c.longitude end),'sha256'),'hex')
            order by m.timestamp,m.id desc
            """, (rs,n)->new Point(rs.getTimestamp(1).toInstant(),rs.getBigDecimal(2)), roomId,parameterId,Timestamp.from(start),Timestamp.from(end));
        BigDecimal weighted=BigDecimal.ZERO, seconds=BigDecimal.ZERO;
        for(int i=0;i+1<points.size();i++) { long span=Math.min(Duration.between(points.get(i).at,points.get(i+1).at).toSeconds(),3600); if(span>0){var s=BigDecimal.valueOf(span);weighted=weighted.add(points.get(i).value.multiply(s));seconds=seconds.add(s);}}
        BigDecimal hours=seconds.divide(BigDecimal.valueOf(3600),2,java.math.RoundingMode.HALF_UP);
        String status=hours.compareTo(MIN_COVERED_HOURS)>=0?"AVAILABLE":"INSUFFICIENT_COVERAGE";
        BigDecimal avg=status.equals("AVAILABLE")?weighted.divide(seconds,2,java.math.RoundingMode.HALF_UP):null;
        String locationHash=jdbc.query("""
            select encode(digest(concat_ws('|',case when c.location_mode='CAMPUS' then cp.latitude else c.latitude end,
              case when c.location_mode='CAMPUS' then cp.longitude else c.longitude end),'sha256'),'hex')
            from contexto_fonte_selecao fs join sensor s on s.external_id=fs.sensor_external_id
            join contexto_compartilhado c on c.id=s.contexto_compartilhado_id left join campus cp on cp.nome=c.campus_nome
            where fs.compartimento_id=? and fs.parametro_def_id=?
            """,(rs,n)->rs.getString(1),roomId,parameterId).stream().findFirst().orElse(null);
        return new SevenDayAverageResponse(status,avg,"°C",start,end,timezone,hours,MIN_COVERED_HOURS,points.size(),
                "time-weighted step average; duplicate timestamps collapsed; gaps capped at 1h; minimum 120 covered hours",locationHash);
    }

    private IntegrationResponse integration(UUID id) {
        return jdbc.query("select * from contexto_integracao_externa where id=?",(rs,n)->new IntegrationResponse(
                rs.getObject("id",UUID.class),rs.getObject("contexto_id",UUID.class),rs.getString("sensor_external_id"),
                rs.getString("nome"),rs.getBoolean("ativo"),rs.getString("descricao"),rs.getString("provider_name"),rs.getString("attribution"),
                rs.getString("endpoint_url"),rs.getString("metodo"),rs.getString("content_type"),rs.getString("request_body"),rs.getString("auth_mode"),
                rs.getBytes("credential_ciphertext")!=null,
                rs.getString("credential_header"),rs.getString("credential_query_param"),
                rs.getString("coordinate_query_param"),rs.getString("query_params_json"),
                rs.getString("headers_json"),readMappings(rs.getString("mappings_json")),rs.getString("temperature_json_pointer"),
                rs.getString("observed_at_json_pointer"),rs.getString("observed_at_format"),rs.getString("timestamp_timezone"),rs.getString("source_id_json_pointer"),
                rs.getInt("interval_seconds"),rs.getInt("timeout_millis"),rs.getInt("max_attempts"),rs.getInt("retry_delay_millis"),
                rs.getInt("validity_seconds"),rs.getBoolean("automatic_collection"),
                instant(rs.getTimestamp("next_run_at")),instant(rs.getTimestamp("last_success_at")),rs.getString("last_outcome"),
                instant(rs.getTimestamp("last_observation_at")),rs.getString("last_error")),id).stream()
                .findFirst().orElseThrow(()->new NotFoundException("Integration not found id="+id));
    }
    private List<String> rooms(UUID id){return jdbc.query("select compartimento_id from contexto_compartilhado_compartimento where contexto_id=? and ativo order by compartimento_id",(rs,n)->rs.getString(1),id);}
    private void validateContext(ContextRequest r){if(r==null||r.nome()==null||r.nome().isBlank())throw new IllegalArgumentException("nome is required");zone(r.timezone());String mode=locationMode(r.locationMode());if("OWN".equals(mode)){if((r.latitude()==null)!=(r.longitude()==null))throw new IllegalArgumentException("latitude and longitude must be supplied together");range(r.latitude(),-90,90,"latitude");range(r.longitude(),-180,180,"longitude");if(r.campusNome()!=null&&!r.campusNome().isBlank())throw new IllegalArgumentException("campusNome is only allowed in CAMPUS mode");}else{if(r.campusNome()==null||r.campusNome().isBlank())throw new IllegalArgumentException("campusNome is required in CAMPUS mode");if(r.latitude()!=null||r.longitude()!=null)throw new IllegalArgumentException("coordinates must be absent in CAMPUS mode");Integer found=jdbc.queryForObject("select count(*) from campus where nome=?",Integer.class,r.campusNome().trim());if(found==null||found==0)throw new NotFoundException("Campus not found nome="+r.campusNome());}}
    private void validateIntegration(IntegrationRequest r){if(r==null||r.sensorExternalId()==null||r.nome()==null||r.endpointUrl()==null)throw new IllegalArgumentException("sensor, name and URL are required");if((r.mappings()==null||r.mappings().isEmpty())&&(r.temperatureJsonPointer()==null||r.temperatureJsonPointer().isBlank()))throw new IllegalArgumentException("at least one response mapping is required");if(nvl(r.intervalSeconds(),900)<60||nvl(r.maxAttempts(),2)<1||nvl(r.maxAttempts(),2)>5||nvl(r.retryDelayMillis(),500)<0||nvl(r.validitySeconds(),3600)<=0)throw new IllegalArgumentException("invalid collection limits");String auth=authMode(r.authMode());if("QUERY".equals(auth)&&(r.credentialQueryParam()==null||r.credentialQueryParam().isBlank()))throw new IllegalArgumentException("credentialQueryParam is required for query authentication");if("HEADER".equals(auth)&&(r.credentialHeader()==null||r.credentialHeader().isBlank()))throw new IllegalArgumentException("credentialHeader is required for header authentication");if("QUERY".equals(auth)&&containsQueryParameter(r.endpointUrl(),r.credentialQueryParam()))throw new IllegalArgumentException("provider credential must not be included in endpointUrl");if("QUERY".equals(auth)&&r.queryParamsJson()!=null&&java.util.regex.Pattern.compile("\\\""+java.util.regex.Pattern.quote(r.credentialQueryParam())+"\\\"\\s*:").matcher(r.queryParamsJson()).find())throw new IllegalArgumentException("provider credential must not be included in queryParamsJson");validateHeaders(r.headersJson());validateMappings(r);}
    private static boolean containsQueryParameter(String url,String name){try{String query=java.net.URI.create(url).getRawQuery();if(query==null)return false;for(String part:query.split("&")){String key=part.split("=",2)[0];if(java.net.URLDecoder.decode(key,java.nio.charset.StandardCharsets.UTF_8).equals(name))return true;}return false;}catch(RuntimeException ex){throw new IllegalArgumentException("endpointUrl is invalid");}}
    private static String locationMode(String v){String m=v==null||v.isBlank()?"OWN":v.trim().toUpperCase();if(!m.equals("OWN")&&!m.equals("CAMPUS"))throw new IllegalArgumentException("locationMode must be OWN or CAMPUS");return m;}
    private static String campus(ContextRequest r){return "CAMPUS".equals(locationMode(r.locationMode()))?r.campusNome().trim():null;}
    private static BigDecimal ownLatitude(ContextRequest r){return "OWN".equals(locationMode(r.locationMode()))?r.latitude():null;}
    private static BigDecimal ownLongitude(ContextRequest r){return "OWN".equals(locationMode(r.locationMode()))?r.longitude():null;}
    private static String authMode(String v){String m=v==null||v.isBlank()?"NONE":v.trim().toUpperCase();if(!Set.of("NONE","HEADER","QUERY","BEARER").contains(m))throw new IllegalArgumentException("invalid authMode");return m;}
    private static String observedFormat(String v){String f=v==null||v.isBlank()?"ISO_8601":v.trim().toUpperCase();if("ISO_INSTANT".equals(f))f="ISO_8601";if(!Set.of("ISO_8601","UNIX_SECONDS","UNIX_MILLISECONDS","COLLECTION_TIME").contains(f))throw new IllegalArgumentException("invalid observedAtFormat");return f;}
    private static void range(BigDecimal v,int min,int max,String field){if(v!=null&&(v.compareTo(BigDecimal.valueOf(min))<0||v.compareTo(BigDecimal.valueOf(max))>0))throw new IllegalArgumentException(field+" is out of range");}
    private static String zone(String z){String value=z==null||z.isBlank()?"America/Sao_Paulo":z.trim();ZoneId.of(value);return value;}
    private static String method(String m){String value=m==null?"GET":m.trim().toUpperCase();if(!value.equals("GET")&&!value.equals("POST"))throw new IllegalArgumentException("method must be GET or POST");return value;}
    private static int nvl(Integer v,int d){return v==null?d:v;} private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private static Instant instant(Timestamp t){return t==null?null:t.toInstant();}
    private int count(String sql,Object... args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private String mappingsJson(IntegrationRequest r){try{return json.writeValueAsString(r.mappings()==null?List.of():r.mappings());}catch(Exception ex){throw new IllegalArgumentException("mappings are invalid");}}
    private List<HttpMappingRequest> readMappings(String value){try{return value==null?List.of():json.readValue(value,new TypeReference<List<HttpMappingRequest>>(){});}catch(Exception ex){throw new IllegalStateException("stored mappings are invalid");}}
    private String jsonText(String value,String fallback){if(value==null||value.isBlank())return fallback;try{json.readTree(value);return value;}catch(Exception ex){throw new IllegalArgumentException("invalid JSON configuration");}}
    private void validateHeaders(String value){if(value==null||value.isBlank())return;JsonNodeWrapper.validateHeaders(json,value);}
    private void validateMappings(IntegrationRequest r){if(r.mappings()==null)return;for(HttpMappingRequest m:r.mappings()){if(m.parametroDefId()==null||m.jsonPointer()==null||m.jsonPointer().isBlank())throw new IllegalArgumentException("mapping parameter and JSON Pointer are required");Integer ok=jdbc.queryForObject("select count(*) from parametro_def p join sensor s on s.tipo_nome=p.tipo_nome where p.id=? and s.external_id=? and p.ativo",Integer.class,m.parametroDefId(),r.sensorExternalId());if(ok==null||ok!=1)throw new IllegalArgumentException("mapping parameter is incompatible with sensor");}}
    private static final class JsonNodeWrapper {private static final Set<String> FORBIDDEN=Set.of("host","content-length","connection","transfer-encoding");static void validateHeaders(ObjectMapper json,String value){try{var root=json.readTree(value);if(!root.isArray())throw new IllegalArgumentException("headersJson must be an array");for(var row:root){String key=row.path("key").asText().trim().toLowerCase();if(FORBIDDEN.contains(key))throw new IllegalArgumentException("forbidden HTTP header: "+key);}}catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("headersJson is invalid");}}}
    private record Point(Instant at,BigDecimal value){}
}
