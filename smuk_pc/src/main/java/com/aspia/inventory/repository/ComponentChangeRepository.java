package com.aspia.inventory.repository;

import com.aspia.inventory.model.ComponentChange;
import com.aspia.inventory.model.Host;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ComponentChangeRepository extends JpaRepository<ComponentChange, Long> {

    List<ComponentChange> findTop20ByOrderByDetectedAtDesc();

    List<ComponentChange> findTop20ByAcknowledgedFalseOrderByDetectedAtDesc();

    List<ComponentChange> findTop50ByOrderByDetectedAtDesc();

    long countByDetectedAtAfter(LocalDateTime since);

    long countByAcknowledgedFalse();

    long countByAcknowledgedFalseAndDetectedAtAfter(LocalDateTime since);

    @Query("SELECT DISTINCT c.host.id FROM ComponentChange c WHERE c.detectedAt > :since")
    List<Long> findDistinctHostIdsWithChangesAfter(@Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT c.host.id FROM ComponentChange c WHERE c.acknowledged = false")
    List<Long> findDistinctHostIdsWithUnacknowledgedChanges();

    List<ComponentChange> findByHostOrderByDetectedAtDesc(Host host);

    List<ComponentChange> findByHostAndAcknowledgedFalseOrderByDetectedAtDesc(Host host);

    List<ComponentChange> findByHostAndAcknowledgedFalse(Host host);

    List<ComponentChange> findByDetectedAtBetweenOrderByDetectedAtDesc(LocalDateTime from, LocalDateTime to);

    void deleteByHost(Host host);
}
