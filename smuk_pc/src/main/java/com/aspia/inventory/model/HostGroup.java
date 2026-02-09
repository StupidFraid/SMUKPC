package com.aspia.inventory.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "host_group")
public class HostGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "tracked_components")
    private String trackedComponents;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public HostGroup() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getTrackedComponents() { return trackedComponents; }
    public void setTrackedComponents(String trackedComponents) { this.trackedComponents = trackedComponents; }

    public Set<String> getTrackedComponentsSet() {
        if (trackedComponents == null || trackedComponents.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String s : trackedComponents.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostGroup hostGroup = (HostGroup) o;
        return id != null && id.equals(hostGroup.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
