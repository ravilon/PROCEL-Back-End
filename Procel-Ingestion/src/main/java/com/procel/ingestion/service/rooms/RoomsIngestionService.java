package com.procel.ingestion.service.rooms;

import com.procel.ingestion.entity.Campus;
import com.procel.ingestion.entity.Compartimento;
import com.procel.ingestion.entity.Predio;
import com.procel.ingestion.entity.Unidade;
import com.procel.ingestion.repository.CampusRepository;
import com.procel.ingestion.repository.CompartimentoRepository;
import com.procel.ingestion.repository.PredioRepository;
import com.procel.ingestion.repository.UnidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class RoomsIngestionService {

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

    public int ingest(List<RoomRecord> rooms) {
        Map<String, Campus> campusByNome = new HashMap<>();
        Map<String, Unidade> unidadeByNome = new HashMap<>();
        Map<String, Predio> predioByKey = new HashMap<>();

        int processed = 0;

        for (RoomRecord r : rooms) {
            if (r.externalId() == null) continue;

            String campusNome = TextNorm.norm(r.campusNome());
            String predioNome = TextNorm.norm(r.predioNome());
            String unidadeNome = TextNorm.norm(r.unidadeNome());
            String compNome = TextNorm.norm(r.compartimentoNome());
            String tipo = TextNorm.norm(r.tipo());
            String lotacaoRaw = TextNorm.norm(r.lotacaoRaw());

            if (campusNome == null || predioNome == null || unidadeNome == null || compNome == null) {
                continue;
            }

            Campus campus = campusByNome.computeIfAbsent(campusNome,
                    n -> campusRepo.findByNome(n).orElseGet(() -> campusRepo.save(new Campus(n)))
            );

            Unidade unidade = unidadeByNome.computeIfAbsent(unidadeNome,
                    n -> unidadeRepo.findByNome(n).orElseGet(() -> unidadeRepo.save(new Unidade(n)))
            );

            String predioKey = campus.getId() + "|" + predioNome;
            Predio predio = predioByKey.computeIfAbsent(predioKey,
                    k -> predioRepo.findByCampus_IdAndNome(campus.getId(), predioNome)
                            .orElseGet(() -> predioRepo.save(new Predio(campus, predioNome)))
            );

            Compartimento comp = compartRepo.findByExternalId(r.externalId())
                    .orElseGet(Compartimento::new);

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
            processed++;
        }

        return processed;
    }
}