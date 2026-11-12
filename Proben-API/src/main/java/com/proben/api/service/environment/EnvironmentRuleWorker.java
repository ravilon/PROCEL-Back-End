package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse;
import com.proben.api.observability.ApiObservabilityMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.*;
import java.util.*;

/**
 * Processador em segundo plano responsavel por environment rule.
 */
@Component
public class EnvironmentRuleWorker {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentRuleWorker.class);
    private final EnvironmentRuleService service;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final ApiObservabilityMetrics metrics;
    private final String workerId;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration lease;

    public EnvironmentRuleWorker(EnvironmentRuleService service, JdbcTemplate jdbc,
                                 TransactionTemplate transactions, ObjectMapper json,
                                 ApiObservabilityMetrics metrics,
                                 @Value("${proben.environment-rules.worker.enabled:true}") boolean enabled,
                                 @Value("${proben.environment-rules.worker.batch-size:20}") int batchSize,
                                 @Value("${proben.environment-rules.worker.max-attempts:5}") int maxAttempts,
                                 @Value("${proben.environment-rules.worker.lease:PT2M}") Duration lease) {
        this.service=service; this.jdbc=jdbc; this.transactions=transactions; this.json=json; this.metrics=metrics;
        this.enabled=enabled; this.batchSize=Math.max(1,batchSize); this.maxAttempts=Math.max(1,maxAttempts); this.lease=lease;
        this.workerId=workerId();
        metrics.registerEnvironmentRuleBacklogGauge(this, this::backlog);
    }

    @Scheduled(fixedDelayString="${proben.environment-rules.worker.fixed-delay:30s}")
    public void poll() { if (enabled) { materializeDue(Instant.now()); processAvailable(); } }

    public int materializeDue(Instant now) {
        int created=0;
        for (RuleResponse rule : service.list(null,null,null,null,true)) {
            ZoneId zone=ZoneId.of(rule.timezone());
            ZonedDateTime cursor=now.minus(Duration.ofMinutes(2)).atZone(zone);
            ZonedDateTime due=CronExpression.parse(rule.cronExpression()).next(cursor);
            while (due!=null && !due.toInstant().isAfter(now)) {
                Instant scheduled=due.toInstant();
                if (rule.diasSemana().contains(due.getDayOfWeek().getValue()) && inForce(rule,scheduled))
                    created += materialize(rule,scheduled);
                due=CronExpression.parse(rule.cronExpression()).next(due);
            }
        }
        return created;
    }

    private int materialize(RuleResponse rule, Instant scheduled) {
        List<String> rooms;
        if ("ROOM".equals(rule.escopo())) rooms=List.of(rule.compartimentoId());
        else rooms=jdbc.query("select id from compartimento order by id",(rs,n)->rs.getString(1));
        int before=countExecutions();
        for(String room:rooms) {
            var resolution=service.resolve(room,rule.categoria());
            if(resolution.effective()!=null && resolution.effective().id().equals(rule.id()))
                service.enqueue(rule,room,resolution.origem(),"SCHEDULED",scheduled,"SCHEDULED","SYSTEM_SCHEDULER");
        }
        return Math.max(0,countExecutions()-before);
    }

    public int processAvailable() {
        List<Claim> claims=transactions.execute(status->jdbc.query("""
            with candidates as (
              select id,status='PROCESSING' and lease_until<=now() recovered
              from regra_ambiente_execucao
              where attempts<? and ((status in ('PENDING','RETRY') and available_at<=now()) or (status='PROCESSING' and lease_until<=now()))
              order by available_at,created_at for update skip locked limit ?
            )
            update regra_ambiente_execucao e set status='PROCESSING',attempts=e.attempts+1,claimed_at=now(),started_at=coalesce(started_at,now()),
              lease_until=now()+(?*interval '1 second'),lease_token=gen_random_uuid(),last_error=null
            from candidates c where e.id=c.id returning e.id,e.regra_id,e.regra_versao,e.compartimento_id,e.instante_previsto,e.lease_token,e.attempts,c.recovered
            """,(rs,n)->new Claim(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4),
                rs.getTimestamp(5).toInstant(),rs.getObject(6,UUID.class),rs.getInt(7),rs.getBoolean(8)),maxAttempts,batchSize,Math.max(1,lease.toSeconds())));
        if(claims==null)return 0;
        for(Claim claim:claims) {
            if (claim.recovered) metrics.environmentRuleLeaseRecovered();
            process(claim);
        }
        return claims.size();
    }

    private void process(Claim claim) {
        Instant started=Instant.now();
        try {
            RuleResponse snapshot=readSnapshot(claim.id);
            if(snapshot.versao()!=claim.version) throw new IllegalStateException("stored rule snapshot/version mismatch");
            var evaluated=service.evaluate(snapshot,claim.room,claim.scheduled,true,claim.id);
            boolean completed=complete(claim,evaluated,started);
            if(completed) {
                metrics.environmentRuleProcessed("MANUAL".equals(executionType(claim.id))?"manual":"scheduled",evaluated.result(),Duration.between(started,Instant.now()));
                log.info("application=proben-api event=environment_rule_processed executionId={} ruleId={} roomId={} result={} attempts={} workerId={}",claim.id,claim.ruleId,claim.room,evaluated.result(),claim.attempts,workerId);
            }
        } catch (RuntimeException ex) {
            retryOrFail(claim,root(ex),started);
        }
    }

    private boolean complete(Claim claim, EnvironmentRuleService.Evaluation e, Instant started) {
        var evidence=enrichedEvidence(claim,e);
        Boolean completed=transactions.execute(status->{
            var owned=jdbc.query("select id from regra_ambiente_execucao where id=? and status='PROCESSING' and lease_token=? and lease_until>now() for update",
                    (rs,n)->rs.getObject(1,UUID.class),claim.id,claim.token);
            if(owned.isEmpty())return false;
            var launches=json.createArrayNode();
            if(e.points()>0 && "SATISFIED".equals(e.result())) for(String person:e.beneficiaryIds()) {
                var ids=jdbc.query("""
                    insert into pontos_lancamento(pessoa_id,tipo,pontos,chave_idempotencia,descricao,created_at,created_by,origem,regra_ambiente_execucao_id,compartimento_id)
                    values (?,'CONCESSAO',?,?,'Regra de ambiente',now(),'SYSTEM_ENVIRONMENT_RULE','ENVIRONMENT_RULE',?,?)
                    on conflict(chave_idempotencia) do nothing returning id
                    """,(rs,n)->rs.getObject(1,UUID.class),person,e.points(),"pontos:ambiente:"+claim.id+":"+person,claim.id,claim.room);
                ids.forEach(id->launches.add(id.toString()));
            }
            int changed=jdbc.update("""
                update regra_ambiente_execucao set status='COMPLETED',resultado=?,motivo=?,evidencias=?::jsonb,contexto_academico=?::jsonb,
                  ocupacao=?::jsonb,beneficiarios=?::jsonb,pontos_calculados=?,lancamentos=?::jsonb,instante_real=now(),finished_at=now(),
                  duration_ms=?,lease_until=null,lease_token=null where id=? and lease_token=?
                """,e.result(),e.reason(),string(evidence),string(e.academic()),string(e.occupancy()),string(e.beneficiaries()),e.points(),
                    string(launches),Duration.between(started,Instant.now()).toMillis(),claim.id,claim.token);
            if(changed==1 && !launches.isEmpty()) metrics.environmentRulePoints(e.points()*launches.size());
            return changed==1;
        });
        return Boolean.TRUE.equals(completed);
    }

    private com.fasterxml.jackson.databind.JsonNode enrichedEvidence(Claim claim, EnvironmentRuleService.Evaluation evaluation) {
        var evidence=evaluation.evidence()!=null&&evaluation.evidence().isObject()
                ? ((com.fasterxml.jackson.databind.node.ObjectNode)evaluation.evidence()).deepCopy()
                : json.createObjectNode();
        String campus=jdbc.queryForObject("""
            select p.campus_id from compartimento c join predio p on p.id=c.predio_id where c.id=?
            """,String.class,claim.room);
        evidence.set("_execution",json.createObjectNode().put("roomId",claim.room).put("campus",campus));
        return evidence;
    }

    private void retryOrFail(Claim c,String error,Instant started) {
        boolean terminal=c.attempts>=maxAttempts;
        int changed=jdbc.update("""
            update regra_ambiente_execucao set status=?,resultado=case when ? then 'FAILED' else resultado end,
              motivo=case when ? then ? else motivo end,last_error=?,available_at=now()+(?*interval '1 second'),
              finished_at=case when ? then now() else null end,duration_ms=?,lease_until=null,lease_token=null
            where id=? and status='PROCESSING' and lease_token=? and lease_until>now()
            """,terminal?"FAILED":"RETRY",terminal,terminal,error,error,Math.min(300,1L<<Math.min(c.attempts,8)),terminal,
                Duration.between(started,Instant.now()).toMillis(),c.id,c.token);
        if(changed==1) metrics.environmentRuleRetry(terminal);
        log.warn("application=proben-api event=environment_rule_failed executionId={} status={} attempts={} reason={}",c.id,terminal?"failed":"retry",c.attempts,error);
    }

    private RuleResponse readSnapshot(UUID id){String value=jdbc.queryForObject("select regra_snapshot::text from regra_ambiente_execucao where id=?",String.class,id);try{return json.readValue(value,RuleResponse.class);}catch(Exception ex){throw new IllegalStateException("invalid rule snapshot",ex);}}
    private String executionType(UUID id){return jdbc.queryForObject("select tipo_execucao from regra_ambiente_execucao where id=?",String.class,id);}
    private boolean inForce(RuleResponse r,Instant at){return (r.vigenciaInicio()==null||!at.isBefore(r.vigenciaInicio()))&&(r.vigenciaFim()==null||at.isBefore(r.vigenciaFim()));}
    private int countExecutions(){Integer n=jdbc.queryForObject("select count(*) from regra_ambiente_execucao",Integer.class);return n==null?0:n;}
    private long backlog(){Long n=jdbc.queryForObject("select count(*) from regra_ambiente_execucao where status in ('PENDING','RETRY','PROCESSING')",Long.class);return n==null?0:n;}
    private String string(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private String root(Throwable t){Throwable c=t;while(c.getCause()!=null)c=c.getCause();String m=c.getMessage();if(m==null)m=c.getClass().getSimpleName();return m.length()>1000?m.substring(0,1000):m;}
    private String workerId(){try{return InetAddress.getLocalHost().getHostName()+"-"+UUID.randomUUID();}catch(Exception ex){return "api-"+UUID.randomUUID();}}
    private record Claim(UUID id,UUID ruleId,int version,String room,Instant scheduled,UUID token,int attempts,boolean recovered){}
}
