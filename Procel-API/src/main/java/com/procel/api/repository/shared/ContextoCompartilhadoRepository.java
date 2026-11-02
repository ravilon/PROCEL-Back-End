package com.procel.api.repository.shared;

import com.procel.api.entity.shared.ContextoCompartilhado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ContextoCompartilhadoRepository extends JpaRepository<ContextoCompartilhado, UUID> {}
