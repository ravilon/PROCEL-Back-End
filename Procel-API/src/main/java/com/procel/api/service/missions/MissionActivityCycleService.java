package com.procel.api.service.missions;

import com.procel.api.entity.missions.Atividade;
import com.procel.api.entity.missions.AtividadeEvento;
import com.procel.api.entity.missions.AtividadeEventoTipo;
import com.procel.api.entity.missions.AtividadeStatus;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.missions.MissaoCicloTipo;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.AtividadeEventoRepository;
import com.procel.api.repository.missions.AtividadeRepository;
import com.procel.api.repository.missions.EventoOcorrenciaRepository;
import com.procel.api.repository.missions.MissaoRepository;
import com.procel.api.repository.people.PessoaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class MissionActivityCycleService {

    private final AtividadeRepository atividadeRepository;
    private final AtividadeEventoRepository atividadeEventoRepository;
    private final MissaoRepository missaoRepository;
    private final PessoaRepository pessoaRepository;
    private final EventoOcorrenciaRepository eventoOcorrenciaRepository;
    private final JdbcTemplate jdbcTemplate;

    public MissionActivityCycleService(
            AtividadeRepository atividadeRepository,
            AtividadeEventoRepository atividadeEventoRepository,
            MissaoRepository missaoRepository,
            PessoaRepository pessoaRepository,
            EventoOcorrenciaRepository eventoOcorrenciaRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.atividadeRepository = atividadeRepository;
        this.atividadeEventoRepository = atividadeEventoRepository;
        this.missaoRepository = missaoRepository;
        this.pessoaRepository = pessoaRepository;
        this.eventoOcorrenciaRepository = eventoOcorrenciaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Atividade localizarOuCriar(CreateCycleActivityCommand command) {
        validate(command);
        Missao missao = missaoRepository.findById(command.missaoId())
                .orElseThrow(() -> new NotFoundException("Missao not found id=" + command.missaoId()));
        if (!pessoaRepository.existsById(command.pessoaId())) {
            throw new NotFoundException("Pessoa not found id=" + command.pessoaId());
        }

        var existing = atividadeRepository.findByPessoaIdAndMissaoIdAndChaveCiclo(
                command.pessoaId(),
                command.missaoId(),
                command.chaveCiclo());
        if (existing.isPresent()) {
            Atividade atividade = existing.get();
            validateEquivalent(atividade, missao, command);
            return atividade;
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into atividade (
                    id, pessoa_id, missao_id, status, assigned_at, chave_ciclo, ciclo_tipo,
                    ciclo_inicio, ciclo_fim, progresso_atual, progresso_necessario,
                    conclusao_automatica
                )
                values (?, ?, ?, ?, now(), ?, ?, ?, ?, 0, ?, ?)
                on conflict (pessoa_id, missao_id, chave_ciclo) do nothing
                """,
                id,
                command.pessoaId(),
                command.missaoId(),
                AtividadeStatus.PENDENTE.name(),
                command.chaveCiclo(),
                command.cicloTipo().name(),
                timestamp(command.cicloInicio()),
                timestamp(command.cicloFim()),
                missao.getProgressoNecessario(),
                missao.isConclusaoAutomatica());

        Atividade atividade = atividadeRepository.findByPessoaIdAndMissaoIdAndChaveCiclo(
                command.pessoaId(),
                command.missaoId(),
                command.chaveCiclo()
        ).orElseThrow(() -> new IllegalStateException("Activity was not created or found"));
        validateEquivalent(atividade, missao, command);
        return atividade;
    }

    @Transactional
    public AtividadeEvento registrarEvento(RegisterActivityEventCommand command) {
        validate(command);
        Atividade atividade = atividadeRepository.findById(command.atividadeId())
                .orElseThrow(() -> new NotFoundException("Atividade not found id=" + command.atividadeId()));
        EventoOcorrencia ocorrencia = eventoOcorrenciaRepository.findById(command.eventoOcorrenciaId())
                .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + command.eventoOcorrenciaId()));

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into atividade_evento (
                    id, atividade_id, evento_ocorrencia_id, tipo, progresso_adicionado, processado_em
                )
                values (?, ?, ?, ?, ?, ?)
                on conflict (atividade_id, evento_ocorrencia_id, tipo) do nothing
                """,
                id,
                atividade.getId(),
                ocorrencia.getId(),
                command.tipo().name(),
                command.progressoAdicionado(),
                timestamp(command.processadoEm()));

        AtividadeEvento evento = atividadeEventoRepository.findByAtividadeIdAndEventoOcorrenciaIdAndTipo(
                atividade.getId(),
                ocorrencia.getId(),
                command.tipo()
        ).orElseThrow(() -> new IllegalStateException("Activity event was not created or found"));

        if (evento.getProgressoAdicionado() != command.progressoAdicionado()) {
            throw new ConflictException("AtividadeEvento key reused with different progressoAdicionado");
        }
        return evento;
    }

    private static void validate(CreateCycleActivityCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.pessoaId() == null || command.pessoaId().isBlank()) throw new IllegalArgumentException("pessoaId is required");
        if (command.missaoId() == null) throw new IllegalArgumentException("missaoId is required");
        if (command.chaveCiclo() == null || command.chaveCiclo().isBlank()) throw new IllegalArgumentException("chaveCiclo is required");
        if (command.cicloTipo() == null) throw new IllegalArgumentException("cicloTipo is required");
        if (command.cicloInicio() != null && command.cicloFim() != null && command.cicloInicio().isAfter(command.cicloFim())) {
            throw new IllegalArgumentException("cicloInicio cannot be after cicloFim");
        }
    }

    private static void validate(RegisterActivityEventCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.atividadeId() == null) throw new IllegalArgumentException("atividadeId is required");
        if (command.eventoOcorrenciaId() == null) throw new IllegalArgumentException("eventoOcorrenciaId is required");
        if (command.tipo() == null) throw new IllegalArgumentException("tipo is required");
        if (command.progressoAdicionado() < 0) throw new IllegalArgumentException("progressoAdicionado must be >= 0");
    }

    private static void validateEquivalent(Atividade atividade, Missao missao, CreateCycleActivityCommand command) {
        if (atividade.getCicloTipo() != command.cicloTipo()
                || atividade.getProgressoNecessario() != missao.getProgressoNecessario()
                || atividade.isConclusaoAutomatica() != missao.isConclusaoAutomatica()
                || !Objects.equals(atividade.getCicloInicio(), command.cicloInicio())
                || !Objects.equals(atividade.getCicloFim(), command.cicloFim())) {
            throw new ConflictException("Activity cycle key reused with incompatible configuration");
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public record CreateCycleActivityCommand(
            String pessoaId,
            UUID missaoId,
            String chaveCiclo,
            MissaoCicloTipo cicloTipo,
            Instant cicloInicio,
            Instant cicloFim
    ) {
    }

    public record RegisterActivityEventCommand(
            UUID atividadeId,
            UUID eventoOcorrenciaId,
            AtividadeEventoTipo tipo,
            int progressoAdicionado,
            Instant processadoEm
    ) {
    }

}
