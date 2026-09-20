package com.procel.api.service.missions;

import com.procel.api.dto.missions.EventoDTOs;
import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoPapel;
import com.procel.api.entity.missions.EventoCondicaoFonte;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.RegraParametro;
import com.procel.api.entity.sensors.RegraOperador;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.EventoCondicaoRepository;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.repository.missions.MissaoRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.RegraParametroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EventoDefinicaoService {

    private final EventoDefinicaoRepository eventoRepo;
    private final EventoCondicaoRepository condicaoRepo;
    private final MissaoRepository missaoRepo;
    private final ParametroDefRepository parametroDefRepo;
    private final RegraParametroRepository regraRepo;

    @Autowired
    public EventoDefinicaoService(
            EventoDefinicaoRepository eventoRepo,
            EventoCondicaoRepository condicaoRepo,
            MissaoRepository missaoRepo,
            ParametroDefRepository parametroDefRepo,
            RegraParametroRepository regraRepo
    ) {
        this.eventoRepo = eventoRepo;
        this.condicaoRepo = condicaoRepo;
        this.missaoRepo = missaoRepo;
        this.parametroDefRepo = parametroDefRepo;
        this.regraRepo = regraRepo;
    }
    public EventoDefinicaoService(EventoDefinicaoRepository eventoRepo,
            EventoCondicaoRepository condicaoRepo, MissaoRepository missaoRepo,
            ParametroDefRepository parametroDefRepo) {
        this(eventoRepo, condicaoRepo, missaoRepo, parametroDefRepo, null);
    }

    @Transactional
    public EventoDTOs.EventoDefinicaoResponse criarEvento(
            UUID missaoId,
            EventoDTOs.EventoDefinicaoRequest req
    ) {
        Missao missao = findMissao(missaoId);
        validateEventoRequest(req, missao);

        EventoDefinicao evento = new EventoDefinicao();
        evento.setMissao(missao);
        applyEvento(evento, req);
        return toEventoResponse(eventoRepo.save(evento));
    }

    @Transactional(readOnly = true)
    public List<EventoDTOs.EventoDefinicaoResponse> listarEventosDaMissao(UUID missaoId) {
        if (missaoId == null) throw new IllegalArgumentException("missionId is required");
        if (!missaoRepo.existsById(missaoId)) {
            throw new NotFoundException("Missao not found id=" + missaoId);
        }
        return eventoRepo.findByMissaoIdOrderByOrdemAscCreatedAtAsc(missaoId)
                .stream()
                .map(this::toEventoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventoDTOs.EventoDefinicaoResponse buscarEvento(UUID eventoId) {
        return toEventoResponse(findEvento(eventoId));
    }

    @Transactional
    public EventoDTOs.EventoDefinicaoResponse atualizarEvento(
            UUID eventoId,
            EventoDTOs.EventoDefinicaoRequest req
    ) {
        EventoDefinicao evento = findEvento(eventoId);
        validateEventoRequest(req, evento.getMissao());
        applyEvento(evento, req);
        return toEventoResponse(evento);
    }

    @Transactional
    public void removerEvento(UUID eventoId) {
        EventoDefinicao evento = findEvento(eventoId);
        evento.setAtivo(false);
    }

    @Transactional
    public EventoDTOs.EventoCondicaoResponse criarCondicao(
            UUID eventoId,
            EventoDTOs.EventoCondicaoRequest req
    ) {
        EventoDefinicao evento = findEvento(eventoId);
        validateCondicaoRequest(req, null, eventoId);
        ParametroDef parametroDef = findParametro(req.parametroDefId());
        validateCondicaoSource(parametroDef, req);

        EventoCondicao condicao = new EventoCondicao();
        condicao.setEventoDefinicao(evento);
        applyCondicao(condicao, parametroDef, req);
        return toCondicaoResponse(condicaoRepo.save(condicao));
    }

    @Transactional
    public EventoDTOs.EventoCondicaoResponse atualizarCondicao(
            UUID eventoId,
            UUID condicaoId,
            EventoDTOs.EventoCondicaoRequest req
    ) {
        EventoCondicao condicao = findCondicaoDoEvento(eventoId, condicaoId);
        validateCondicaoRequest(req, condicaoId, eventoId);
        ParametroDef parametroDef = findParametro(req.parametroDefId());
        validateCondicaoSource(parametroDef, req);
        applyCondicao(condicao, parametroDef, req);
        return toCondicaoResponse(condicao);
    }

    @Transactional
    public void removerCondicao(UUID eventoId, UUID condicaoId) {
        EventoCondicao condicao = findCondicaoDoEvento(eventoId, condicaoId);
        condicaoRepo.delete(condicao);
    }

    private Missao findMissao(UUID missaoId) {
        if (missaoId == null) throw new IllegalArgumentException("missionId is required");
        return missaoRepo.findById(missaoId)
                .orElseThrow(() -> new NotFoundException("Missao not found id=" + missaoId));
    }

    private EventoDefinicao findEvento(UUID eventoId) {
        if (eventoId == null) throw new IllegalArgumentException("eventId is required");
        return eventoRepo.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("EventoDefinicao not found id=" + eventoId));
    }

    private ParametroDef findParametro(UUID parametroDefId) {
        return parametroDefRepo.findById(parametroDefId)
                .orElseThrow(() -> new NotFoundException("ParametroDef not found id=" + parametroDefId));
    }

    private EventoCondicao findCondicaoDoEvento(UUID eventoId, UUID condicaoId) {
        if (eventoId == null) throw new IllegalArgumentException("eventId is required");
        if (condicaoId == null) throw new IllegalArgumentException("conditionId is required");
        EventoCondicao condicao = condicaoRepo.findById(condicaoId)
                .orElseThrow(() -> new NotFoundException("EventoCondicao not found id=" + condicaoId));
        if (!condicao.getEventoDefinicao().getId().equals(eventoId)) {
            throw new NotFoundException(
                    "EventoCondicao not found eventId=" + eventoId + " id=" + condicaoId
            );
        }
        return condicao;
    }

    private void validateEventoRequest(
            EventoDTOs.EventoDefinicaoRequest req,
            Missao missao
    ) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.nome() == null || req.nome().isBlank()) throw new IllegalArgumentException("nome is required");
        require(req.tipoDisparo(), "tipoDisparo");
        require(req.modoAvaliacao(), "modoAvaliacao");
        require(req.operadorLogico(), "operadorLogico");
        require(req.politicaAtribuicao(), "politicaAtribuicao");
        EventoPapel role = req.papel() == null ? EventoPapel.PROGRESSO : req.papel();
        if (role == EventoPapel.ATRIBUICAO && missao.getParent() != null) throw new ConflictException("Assignment belongs to the parent mission");
        if (role != EventoPapel.ATRIBUICAO && missaoRepo.existsByParent_Id(missao.getId())) throw new ConflictException("Parent mission cannot progress or complete directly");
        if (req.quantidadeNecessaria() != null && req.quantidadeNecessaria() < 1) {
            throw new IllegalArgumentException("quantidadeNecessaria must be greater than or equal to 1");
        }
        validateNonNegative(req.janelaSegundos(), "janelaSegundos");
        validateNonNegative(req.duracaoMinimaSegundos(), "duracaoMinimaSegundos");
        validateNonNegative(req.cooldownSegundos(), "cooldownSegundos");
        if (req.lacunaMaximaSegundos() != null && req.lacunaMaximaSegundos() <= 0) throw new IllegalArgumentException("lacunaMaximaSegundos must be positive");
        boolean ativo = req.ativo() == null || req.ativo();
        if (ativo && !missao.isAtivo()) {
            throw new ConflictException("Cannot activate event definition for inactive mission id=" + missao.getId());
        }
    }

    private void validateCondicaoRequest(
            EventoDTOs.EventoCondicaoRequest req,
            UUID existingCondicaoId,
            UUID eventoId
    ) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.parametroDefId() == null) throw new IllegalArgumentException("parametroDefId is required");
        require(req.operador(), "operador");
        require(req.agregacao(), "agregacao");
        if (req.ordem() == null) throw new IllegalArgumentException("ordem is required");
        boolean duplicate = existingCondicaoId == null
                ? condicaoRepo.existsByEventoDefinicaoIdAndAtivoTrueAndOrdem(eventoId, req.ordem())
                : condicaoRepo.existsByEventoDefinicaoIdAndAtivoTrueAndOrdemAndIdNot(
                        eventoId,
                        req.ordem(),
                        existingCondicaoId
                );
        if (duplicate) {
            throw new ConflictException("EventoDefinicao already has condition with ordem=" + req.ordem());
        }
    }

    private void validateCondicaoSource(ParametroDef parametroDef, EventoDTOs.EventoCondicaoRequest req) {
        EventoCondicaoFonte fonte = req.fonte() == null ? EventoCondicaoFonte.PARAMETRO_VALOR : req.fonte();
        if (fonte == EventoCondicaoFonte.AVALIACAO_REGRA) {
            if (req.regraParametroId() == null || req.resultadoEsperado() == null) {
                throw new IllegalArgumentException("regraParametroId and resultadoEsperado are required");
            }
            RegraParametro regra = regraRepo.findById(req.regraParametroId())
                    .orElseThrow(() -> new NotFoundException("RegraParametro not found id=" + req.regraParametroId()));
            if (!regra.isAtivo() || !regra.getParametroDef().getId().equals(parametroDef.getId())) {
                throw new ConflictException("Rule is inactive or belongs to another parameter");
            }
            if (req.operador() != RegraOperador.EQ && req.operador() != RegraOperador.NEQ) {
                throw new IllegalArgumentException("Rule result supports only EQ and NEQ");
            }
            return;
        }
        if (req.regraParametroId() != null || req.resultadoEsperado() != null) {
            throw new IllegalArgumentException("Raw value condition cannot reference a rule");
        }
        validateCondicaoValues(parametroDef, req);
    }
    private static void validateCondicaoValues(
            ParametroDef parametroDef,
            EventoDTOs.EventoCondicaoRequest req
    ) {
        switch (parametroDef.getDataType()) {
            case NUMERIC -> validateNumericCondition(req);
            case BOOLEAN -> validateBooleanCondition(req);
            case TEXT -> validateTextCondition(req);
        }
    }

    private static void validateNumericCondition(EventoDTOs.EventoCondicaoRequest req) {
        if (req.valorBoolean() != null || hasText(req.valorText())) {
            throw new IllegalArgumentException("Numeric conditions only accept numeric values");
        }
        switch (req.operador()) {
            case GT, GTE, LT, LTE, EQ, NEQ -> {
                if (req.valorNumeric1() == null) {
                    throw new IllegalArgumentException("valorNumeric1 is required for numeric operator " + req.operador());
                }
            }
            case BETWEEN, OUTSIDE -> {
                if (req.valorNumeric1() == null || req.valorNumeric2() == null) {
                    throw new IllegalArgumentException("valorNumeric1 and valorNumeric2 are required for numeric operator " + req.operador());
                }
                if (req.valorNumeric2().compareTo(req.valorNumeric1()) < 0) {
                    throw new IllegalArgumentException("valorNumeric2 must be greater than or equal to valorNumeric1");
                }
            }
            case CONTAINS -> throw new IllegalArgumentException("CONTAINS is not supported for numeric parameters");
        }
    }

    private static void validateBooleanCondition(EventoDTOs.EventoCondicaoRequest req) {
        if (req.operador() != RegraOperador.EQ && req.operador() != RegraOperador.NEQ) {
            throw new IllegalArgumentException("Only EQ and NEQ are supported for boolean parameters");
        }
        if (req.valorBoolean() == null) {
            throw new IllegalArgumentException("valorBoolean is required for boolean conditions");
        }
        if (req.valorNumeric1() != null || req.valorNumeric2() != null || hasText(req.valorText())) {
            throw new IllegalArgumentException("Boolean conditions only accept valorBoolean");
        }
    }

    private static void validateTextCondition(EventoDTOs.EventoCondicaoRequest req) {
        if (req.operador() != RegraOperador.EQ
                && req.operador() != RegraOperador.NEQ
                && req.operador() != RegraOperador.CONTAINS) {
            throw new IllegalArgumentException("Only EQ, NEQ and CONTAINS are supported for text parameters");
        }
        if (!hasText(req.valorText())) {
            throw new IllegalArgumentException("valorText is required for text conditions");
        }
        if (req.valorNumeric1() != null || req.valorNumeric2() != null || req.valorBoolean() != null) {
            throw new IllegalArgumentException("Text conditions only accept valorText");
        }
    }

    private static void applyEvento(
            EventoDefinicao evento,
            EventoDTOs.EventoDefinicaoRequest req
    ) {
        evento.setNome(req.nome().trim());
        evento.setDescricao(blankToNull(req.descricao()));
        evento.setTipoDisparo(req.tipoDisparo());
        evento.setPapel(req.papel());
        evento.setModoAvaliacao(req.modoAvaliacao());
        evento.setOperadorLogico(req.operadorLogico());
        evento.setPoliticaAtribuicao(req.politicaAtribuicao());
        evento.setJanelaSegundos(req.janelaSegundos());
        evento.setDuracaoMinimaSegundos(req.duracaoMinimaSegundos());
        evento.setQuantidadeNecessaria(req.quantidadeNecessaria() == null ? 1 : req.quantidadeNecessaria());
        evento.setCooldownSegundos(req.cooldownSegundos());
        evento.setLacunaMaximaSegundos(req.lacunaMaximaSegundos());
        evento.setOrdem(req.ordem() == null ? 0 : req.ordem());
        evento.setAtivo(req.ativo() == null || req.ativo());
    }

    private void applyCondicao(
            EventoCondicao condicao,
            ParametroDef parametroDef,
            EventoDTOs.EventoCondicaoRequest req
    ) {
        condicao.setParametroDef(parametroDef);
        condicao.setFonte(req.fonte());
        condicao.setRegraParametro(req.regraParametroId() == null ? null : regraRepo.getReferenceById(req.regraParametroId()));
        condicao.setResultadoEsperado(req.resultadoEsperado());
        condicao.setOperador(req.operador());
        condicao.setValorNumeric1(req.valorNumeric1());
        condicao.setValorNumeric2(req.valorNumeric2());
        condicao.setValorBoolean(req.valorBoolean());
        condicao.setValorText(blankToNull(req.valorText()));
        condicao.setAgregacao(req.agregacao());
        condicao.setObrigatoria(req.obrigatoria() == null || req.obrigatoria());
        condicao.setOrdem(req.ordem());
        condicao.setAtivo(true);
    }

    private EventoDTOs.EventoDefinicaoResponse toEventoResponse(EventoDefinicao evento) {
        List<EventoDTOs.EventoCondicaoResponse> condicoes = condicaoRepo
                .findByEventoDefinicaoIdAndAtivoTrueOrderByOrdemAscCreatedAtAsc(evento.getId())
                .stream()
                .map(EventoDefinicaoService::toCondicaoResponse)
                .toList();
        return new EventoDTOs.EventoDefinicaoResponse(
                evento.getId(),
                evento.getMissao().getId(),
                evento.getMissao().getTitulo(),
                evento.getNome(),
                evento.getDescricao(),
                evento.getTipoDisparo(),
                evento.getModoAvaliacao(),
                evento.getOperadorLogico(),
                evento.getPoliticaAtribuicao(),
                evento.getJanelaSegundos(),
                evento.getDuracaoMinimaSegundos(),
                evento.getQuantidadeNecessaria(),
                evento.getCooldownSegundos(),
                evento.getOrdem(),
                evento.isAtivo(),
                evento.getCreatedAt(),
                evento.getUpdatedAt(),
                condicoes,
                evento.getPapel(),
                evento.getLacunaMaximaSegundos()
        );
    }

    private static EventoDTOs.EventoCondicaoResponse toCondicaoResponse(EventoCondicao condicao) {
        ParametroDef parametroDef = condicao.getParametroDef();
        return new EventoDTOs.EventoCondicaoResponse(
                condicao.getId(),
                condicao.getEventoDefinicao().getId(),
                parametroDef.getId(),
                parametroDef.getNome(),
                parametroDef.getTipo().getNome(),
                parametroDef.getDataType(),
                condicao.getOperador(),
                condicao.getValorNumeric1(),
                condicao.getValorNumeric2(),
                condicao.getValorBoolean(),
                condicao.getValorText(),
                condicao.getAgregacao(),
                condicao.isObrigatoria(),
                condicao.getOrdem(),
                condicao.isAtivo(),
                condicao.getCreatedAt(),
                condicao.getFonte(),
                condicao.getRegraParametro() == null ? null : condicao.getRegraParametro().getId(),
                condicao.getRegraParametro() == null ? null : condicao.getRegraParametro().getNome(),
                condicao.getResultadoEsperado()
        );
    }

    private static void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be greater than or equal to 0");
        }
    }

    private static void require(Object value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
