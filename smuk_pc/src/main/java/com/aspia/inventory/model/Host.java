package com.aspia.inventory.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "hosts")
public class Host {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aspia_host_id", nullable = false, unique = true)
    private Integer aspiaHostId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "computer_name")
    private String computerName;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "os_name")
    private String osName;

    private String architecture;

    @Column(name = "aspia_version")
    private String aspiaVersion;

    @Column(name = "cpu_model")
    private String cpuModel;

    @Column(name = "total_ram_bytes")
    private Long totalRamBytes;

    @Column(name = "total_disk_bytes")
    private Long totalDiskBytes;

    @Column(name = "video_adapter")
    private String videoAdapter;

    @Column(name = "motherboard")
    private String motherboard;

    @Column(name = "alias")
    private String alias;

    @Column(name = "aspia_host_user")
    private String aspiaHostUser;

    @Column(name = "aspia_host_password")
    private String aspiaHostPasswordEncrypted;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "online")
    private Boolean online = false;

    @Column(name = "sync_error")
    private String syncError;

    @Column(name = "needs_full_sync")
    private boolean needsFullSync = true;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "tracked_components_override")
    private String trackedComponentsOverride;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "hosts_groups",
            joinColumns = @JoinColumn(name = "host_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<HostGroup> groups = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Host() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getAspiaHostId() { return aspiaHostId; }
    public void setAspiaHostId(Integer aspiaHostId) { this.aspiaHostId = aspiaHostId; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getComputerName() { return computerName; }
    public void setComputerName(String computerName) { this.computerName = computerName; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    public String getAspiaVersion() { return aspiaVersion; }
    public void setAspiaVersion(String aspiaVersion) { this.aspiaVersion = aspiaVersion; }

    public String getCpuModel() { return cpuModel; }
    public void setCpuModel(String cpuModel) { this.cpuModel = cpuModel; }

    public Long getTotalRamBytes() { return totalRamBytes; }
    public void setTotalRamBytes(Long totalRamBytes) { this.totalRamBytes = totalRamBytes; }

    public Long getTotalDiskBytes() { return totalDiskBytes; }
    public void setTotalDiskBytes(Long totalDiskBytes) { this.totalDiskBytes = totalDiskBytes; }

    public String getVideoAdapter() { return videoAdapter; }
    public void setVideoAdapter(String videoAdapter) { this.videoAdapter = videoAdapter; }

    public String getMotherboard() { return motherboard; }
    public void setMotherboard(String motherboard) { this.motherboard = motherboard; }

    public boolean isOnline() { return online != null && online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getSyncError() { return syncError; }
    public void setSyncError(String syncError) { this.syncError = syncError; }

    public boolean isNeedsFullSync() { return needsFullSync; }
    public void setNeedsFullSync(boolean needsFullSync) { this.needsFullSync = needsFullSync; }

    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getDisplayName() {
        return alias != null && !alias.trim().isEmpty() ? alias : computerName;
    }

    public String getAspiaHostUser() { return aspiaHostUser; }
    public void setAspiaHostUser(String aspiaHostUser) { this.aspiaHostUser = aspiaHostUser; }

    public String getAspiaHostPasswordEncrypted() { return aspiaHostPasswordEncrypted; }
    public void setAspiaHostPasswordEncrypted(String aspiaHostPasswordEncrypted) { this.aspiaHostPasswordEncrypted = aspiaHostPasswordEncrypted; }

    public Set<HostGroup> getGroups() { return groups; }
    public void setGroups(Set<HostGroup> groups) { this.groups = groups; }

    public String getGroupNamesString() {
        if (groups == null || groups.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (HostGroup g : groups) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(g.getName());
        }
        return sb.toString();
    }

    public String getGroupIdsString() {
        if (groups == null || groups.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (HostGroup g : groups) {
            if (sb.length() > 0) sb.append(",");
            sb.append(g.getId());
        }
        return sb.toString();
    }

    public String getFormattedRam() {
        if (totalRamBytes == null) return "—";
        double gb = totalRamBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.1f GB", gb);
    }

    public String getFormattedDisk() {
        if (totalDiskBytes == null) return "—";
        double gb = totalDiskBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.1f GB", gb);
    }

    public String getTrackedComponentsOverride() { return trackedComponentsOverride; }
    public void setTrackedComponentsOverride(String trackedComponentsOverride) { this.trackedComponentsOverride = trackedComponentsOverride; }

    private static final Set<String> ALL_COMPONENTS = new LinkedHashSet<>(
            Arrays.asList("PROCESSOR", "MEMORY", "DISK", "VIDEO_ADAPTER", "SOFTWARE"));

    public static Set<String> getAllComponentTypes() { return ALL_COMPONENTS; }

    public Set<String> getEffectiveTrackedComponents() {
        if (trackedComponentsOverride != null) {
            return parseCsv(trackedComponentsOverride);
        }
        if (groups != null && !groups.isEmpty()) {
            Set<String> union = new HashSet<>();
            for (HostGroup g : groups) {
                Set<String> gs = g.getTrackedComponentsSet();
                if (!gs.isEmpty()) {
                    union.addAll(gs);
                }
            }
            if (!union.isEmpty()) return union;
        }
        return ALL_COMPONENTS;
    }

    public boolean isComponentTracked(String componentType) {
        return getEffectiveTrackedComponents().contains(componentType);
    }

    public boolean hasTrackingOverride() {
        return trackedComponentsOverride != null;
    }

    private static Set<String> parseCsv(String csv) {
        Set<String> result = new HashSet<>();
        if (csv == null || csv.trim().isEmpty()) return result;
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
}
