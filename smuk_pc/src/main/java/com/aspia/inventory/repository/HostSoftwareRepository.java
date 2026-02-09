package com.aspia.inventory.repository;

import com.aspia.inventory.model.Host;
import com.aspia.inventory.model.HostSoftware;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HostSoftwareRepository extends JpaRepository<HostSoftware, Long> {

    List<HostSoftware> findByHost(Host host);

    @Modifying
    void deleteByHost(Host host);

    @Query("SELECT hs.name, MAX(hs.publisher), COUNT(DISTINCT hs.host.id) FROM HostSoftware hs GROUP BY hs.name ORDER BY COUNT(DISTINCT hs.host.id) DESC")
    List<Object[]> findSoftwareSummary();

    @Query("SELECT hs FROM HostSoftware hs JOIN FETCH hs.host WHERE hs.name = :name ORDER BY hs.host.computerName")
    List<HostSoftware> findByNameWithHost(@Param("name") String name);
}
