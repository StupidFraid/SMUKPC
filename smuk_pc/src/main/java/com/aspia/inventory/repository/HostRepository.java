package com.aspia.inventory.repository;

import com.aspia.inventory.model.Host;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HostRepository extends JpaRepository<Host, Long> {

    Optional<Host> findByAspiaHostId(Integer aspiaHostId);

    List<Host> findByNeedsFullSyncTrue();

    long countByNeedsFullSyncFalse();

    @Query("SELECT COUNT(h) FROM Host h WHERE h.online = true")
    long countByOnlineTrue();

    @Query("SELECT COUNT(h) FROM Host h WHERE h.online = false OR h.online IS NULL")
    long countByOnlineFalse();

    @Query("SELECT COUNT(h) FROM Host h WHERE h.syncError IS NOT NULL")
    long countBySyncErrorNotNull();

    List<Host> findByOsNameNotLikeIgnoreCase(String pattern);

    List<Host> findByOsNameIsNull();
}
