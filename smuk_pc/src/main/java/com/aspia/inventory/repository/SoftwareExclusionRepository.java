package com.aspia.inventory.repository;

import com.aspia.inventory.model.Host;
import com.aspia.inventory.model.SoftwareExclusion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoftwareExclusionRepository extends JpaRepository<SoftwareExclusion, Long> {

    List<SoftwareExclusion> findByHostIsNull();

    List<SoftwareExclusion> findByHost(Host host);

    Optional<SoftwareExclusion> findBySoftwareNameAndHostIsNull(String softwareName);

    Optional<SoftwareExclusion> findBySoftwareNameAndHost(String softwareName, Host host);

    void deleteBySoftwareNameAndHostIsNull(String softwareName);

    void deleteBySoftwareNameAndHost(String softwareName, Host host);
}
