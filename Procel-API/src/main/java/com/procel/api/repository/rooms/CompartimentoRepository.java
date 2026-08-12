package com.procel.api.repository.rooms;

import com.procel.api.entity.rooms.Compartimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CompartimentoRepository extends JpaRepository<Compartimento, String> {
    List<Compartimento> findByTipoIn(Collection<String> tipos);
}
