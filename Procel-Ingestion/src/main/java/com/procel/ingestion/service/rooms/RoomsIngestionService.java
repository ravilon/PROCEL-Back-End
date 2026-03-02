package com.procel.ingestion.service.rooms;

import com.procel.ingestion.entity.rooms.Campus;
import com.procel.ingestion.entity.rooms.Compartimento;
import com.procel.ingestion.entity.rooms.Predio;
import com.procel.ingestion.entity.rooms.Unidade;
import com.procel.ingestion.repository.rooms.CampusRepository;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import com.procel.ingestion.repository.rooms.PredioRepository;
import com.procel.ingestion.repository.rooms.UnidadeRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class RoomsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RoomsIngestionService.class);

    private final CampusRepository campusRepo;
    private final PredioRepository predioRepo;
    private final UnidadeRepository unidadeRepo;
    private final CompartimentoRepository compartRepo;

    public RoomsIngestionService(
            CampusRepository campusRepo,
            PredioRepository predioRepo,
            UnidadeRepository unidadeRepo,
            CompartimentoRepository compartRepo
    ) {
        this.campusRepo = campusRepo;
        this.predioRepo = predioRepo;
        this.unidadeRepo = unidadeRepo;
        this.compartRepo = compartRepo;
    }

    private <T> boolean equalsNullable(T a, T b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public RoomsIngestionResult ingest(List<RoomRecord> rooms) {

        long start = System.currentTimeMillis();

        Map<String, Campus> campusByNome = new HashMap<>();
        Map<String, Unidade> unidadeByNome = new HashMap<>();
        Map<String, Predio> predioByKey = new HashMap<>();

        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        for (RoomRecord r : rooms) {

            // externalId do compartimento é obrigatório para integração
            if (r.externalId() == null) {
                skipped++;
                continue;
            }

            String campusNome = TextNorm.norm(r.campusNome());
            String predioNome = TextNorm.norm(r.predioNome());
            String unidadeNome = TextNorm.norm(r.unidadeNome());
            String compNome = TextNorm.norm(r.compartimentoNome());
            String tipo = TextNorm.norm(r.tipo());
            String lotacaoRaw = TextNorm.norm(r.lotacaoRaw());

            if (campusNome == null || predioNome == null || unidadeNome == null || compNome == null) {
                skipped++;
                continue;
            }

            // CAMPUS (PK natural = nome)
            Campus campus = campusByNome.computeIfAbsent(
                    campusNome,
                    n -> campusRepo.findById(n).orElseGet(() -> campusRepo.save(new Campus(n)))
            );

            // UNIDADE (PK natural = nome)
            Unidade unidade = unidadeByNome.computeIfAbsent(
                    unidadeNome,
                    n -> unidadeRepo.findById(n).orElseGet(() -> unidadeRepo.save(new Unidade(n)))
            );

            // PRÉDIO (PK natural = campusNome|predioNome)
            String predioId = campusNome + "|" + predioNome;

            Predio predio = predioByKey.computeIfAbsent(
                    predioId,
                    k -> predioRepo.findById(k).orElseGet(() -> predioRepo.save(new Predio(campus, predioNome)))
            );

            // COMPARTIMENTO (PK interna Long + externalId Long UNIQUE)
            Compartimento comp = compartRepo.findByExternalId(r.externalId())
                    .orElseGet(Compartimento::new);

            boolean isNew = (comp.getId() == null);

            if (isNew) {
                // INSERT
                comp.setExternalId(r.externalId());
                comp.setPredio(predio);
                comp.setUnidade(unidade);
                comp.setNome(compNome);
                comp.setTipo(tipo != null ? tipo : "nao_informado");
                comp.setPavimento(r.pavimento());
                comp.setCapacidade(r.capacidade());
                comp.setArea(r.area());
                comp.setLotacaoRaw(lotacaoRaw);

                compartRepo.save(comp);
                inserted++;
                continue;
            }

            // UPDATE somente se mudou algo
            boolean changed = false;

            // Importante: comparar pelo ID do prédio (campus|nome), não só pelo nome
            if (comp.getPredio() == null || !comp.getPredio().getId().equals(predio.getId())) {
                comp.setPredio(predio);
                changed = true;
            }

            if (comp.getUnidade() == null || !comp.getUnidade().getNome().equals(unidade.getNome())) {
                comp.setUnidade(unidade);
                changed = true;
            }

            if (comp.getNome() == null || !comp.getNome().equals(compNome)) {
                comp.setNome(compNome);
                changed = true;
            }

            String tipoFinal = (tipo != null ? tipo : "nao_informado");
            if (comp.getTipo() == null || !comp.getTipo().equals(tipoFinal)) {
                comp.setTipo(tipoFinal);
                changed = true;
            }

            if (!equalsNullable(comp.getPavimento(), r.pavimento())) {
                comp.setPavimento(r.pavimento());
                changed = true;
            }

            if (!equalsNullable(comp.getCapacidade(), r.capacidade())) {
                comp.setCapacidade(r.capacidade());
                changed = true;
            }

            if (!equalsNullable(comp.getArea(), r.area())) {
                comp.setArea(r.area());
                changed = true;
            }

            if (!equalsNullable(comp.getLotacaoRaw(), lotacaoRaw)) {
                comp.setLotacaoRaw(lotacaoRaw);
                changed = true;
            }

            if (changed) {
                compartRepo.save(comp);
                updated++;
            } else {
                skipped++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;

        log.info(
                "Rooms sync finished | fetched={} inserted={} updated={} skipped={} elapsedMs={}",
                rooms.size(), inserted, updated, skipped, elapsed
        );

        return new RoomsIngestionResult(
                rooms.size(),
                inserted,
                updated,
                skipped,
                elapsed
        );
    }
}