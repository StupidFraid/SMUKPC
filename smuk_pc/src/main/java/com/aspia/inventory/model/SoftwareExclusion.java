package com.aspia.inventory.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "software_exclusions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"software_name", "host_id"}))
public class SoftwareExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "software_name", nullable = false)
    private String softwareName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id")
    private Host host;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public SoftwareExclusion() {}

    public SoftwareExclusion(String softwareName, Host host) {
        this.softwareName = softwareName;
        this.host = host;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSoftwareName() { return softwareName; }
    public void setSoftwareName(String softwareName) { this.softwareName = softwareName; }

    public Host getHost() { return host; }
    public void setHost(Host host) { this.host = host; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
