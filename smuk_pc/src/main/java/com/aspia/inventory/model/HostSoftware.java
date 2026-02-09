package com.aspia.inventory.model;

import javax.persistence.*;

@Entity
@Table(name = "host_software")
public class HostSoftware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Column(nullable = false)
    private String name;

    private String version;

    private String publisher;

    @Column(name = "install_date")
    private String installDate;

    public HostSoftware() {}

    public HostSoftware(Host host, String name, String version, String publisher, String installDate) {
        this.host = host;
        this.name = name;
        this.version = version;
        this.publisher = publisher;
        this.installDate = installDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Host getHost() { return host; }
    public void setHost(Host host) { this.host = host; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getInstallDate() { return installDate; }
    public void setInstallDate(String installDate) { this.installDate = installDate; }

    public String getSoftwareKey() {
        return name + "|" + (version != null ? version : "");
    }
}
