package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.SensorIntegrationParserStatus;
import com.procel.api.entity.sensors.SensorIntegrationParserVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SensorIntegrationParserVersionRepository extends JpaRepository<SensorIntegrationParserVersion, UUID> {
    List<SensorIntegrationParserVersion> findAllByProfile_IdOrderByVersionDesc(UUID profileId);
    Optional<SensorIntegrationParserVersion> findByProfile_IdAndStatus(UUID profileId, SensorIntegrationParserStatus status);

    @Query("select coalesce(max(v.version), 0) from SensorIntegrationParserVersion v where v.profile.id = :profileId")
    int maxVersionByProfile(@Param("profileId") UUID profileId);

    boolean existsByProfile_IdAndStatusIn(UUID profileId, Collection<SensorIntegrationParserStatus> statuses);
}
