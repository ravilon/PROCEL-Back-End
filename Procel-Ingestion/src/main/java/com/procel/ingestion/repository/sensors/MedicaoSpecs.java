package com.procel.ingestion.repository.sensors;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.procel.ingestion.entity.sensors.Medicao;

public final class MedicaoSpecs {
  private MedicaoSpecs() {}

  public static Specification<Medicao> bySensor(String sensorExternalId) {
    return (root, query, cb) -> cb.equal(root.get("sensor").get("externalId"), sensorExternalId);
  }

  public static Specification<Medicao> byCompartimento(String compartimentoId) {
    return (root, query, cb) ->
        cb.equal(root.get("sensor").get("compartimento").get("id"), compartimentoId);
  }

  public static Specification<Medicao> from(Instant from) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
  }

  public static Specification<Medicao> to(Instant to) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
  }
}