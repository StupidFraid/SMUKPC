package com.aspia.inventory.service;

import com.aspia.inventory.model.ComponentChange;
import com.aspia.inventory.model.Host;
import com.aspia.inventory.model.HostSoftware;
import com.aspia.inventory.model.SoftwareExclusion;
import com.aspia.inventory.repository.ComponentChangeRepository;
import com.aspia.inventory.repository.HostRepository;
import com.aspia.inventory.repository.HostSoftwareRepository;
import com.aspia.inventory.repository.SoftwareExclusionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.aspia.inventory.util.CryptoUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class AspiaSyncService {

    private static final Logger log = LoggerFactory.getLogger(AspiaSyncService.class);

    private final RestTemplate aspiaRestTemplate;
    private final HostRepository hostRepository;
    private final HostSoftwareRepository softwareRepository;
    private final ComponentChangeRepository changeRepository;
    private final SoftwareExclusionRepository exclusionRepository;
    private final TelegramNotificationService telegramService;
    private final TransactionTemplate transactionTemplate;
    private ExecutorService syncExecutor;

    @Value("${aspia.api.base-url}")
    private String apiBaseUrl;

    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Value("${aspia.sync.threads:5}")
    private int syncThreads;

    @Value("${aspia.sync.timeout-minutes:10}")
    private int syncTimeoutMinutes;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile LocalDateTime lastSyncTime;
    private volatile String lastSyncStatus;
    private volatile boolean syncing;

    public AspiaSyncService(RestTemplate aspiaRestTemplate,
                            HostRepository hostRepository,
                            HostSoftwareRepository softwareRepository,
                            ComponentChangeRepository changeRepository,
                            SoftwareExclusionRepository exclusionRepository,
                            TelegramNotificationService telegramService,
                            PlatformTransactionManager transactionManager) {
        this.aspiaRestTemplate = aspiaRestTemplate;
        this.hostRepository = hostRepository;
        this.softwareRepository = softwareRepository;
        this.changeRepository = changeRepository;
        this.exclusionRepository = exclusionRepository;
        this.telegramService = telegramService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    @Transactional
    public void init() {
        syncExecutor = Executors.newFixedThreadPool(syncThreads);
        log.info("Пул синхронизации: {} потоков, таймаут {} мин", syncThreads, syncTimeoutMinutes);
        backfillMotherboard();
    }

    private void backfillMotherboard() {
        int count = 0;
        for (Host host : hostRepository.findAll()) {
            if (host.getMotherboard() == null && host.getConfigJson() != null) {
                try {
                    Map<String, Object> sysInfo = objectMapper.readValue(
                            host.getConfigJson(), new TypeReference<Map<String, Object>>() {});
                    String mb = extractMotherboard(sysInfo);
                    if (mb != null) {
                        host.setMotherboard(mb);
                        hostRepository.save(host);
                        count++;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (count > 0) {
            log.info("Backfill: заполнено поле motherboard у {} хостов", count);
        }
    }

    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public String getLastSyncStatus() { return lastSyncStatus; }
    public boolean isSyncing() { return syncing; }

    /**
     * Ручная синхронизация — только список хостов.
     */
    @Transactional
    public int syncHostList() {
        log.info("Начало синхронизации списка хостов...");
        syncing = true;
        try {
            List<Map<String, Object>> apiHosts = fetchHostListFromApi();
            if (apiHosts == null) {
                lastSyncStatus = "Ошибка: API недоступен";
                return 0;
            }

            // Помечаем все хосты как оффлайн перед обработкой
            for (Host h : hostRepository.findAll()) {
                h.setOnline(false);
                hostRepository.save(h);
            }

            int updated = 0;
            for (Map<String, Object> apiHost : apiHosts) {
                Integer aspiaHostId = toInt(apiHost.get("host_id"));
                Long sessionId = toLong(apiHost.get("session_id"));
                if (aspiaHostId == null) continue;

                // Пропускаем не-Windows хосты (Linux — служебные: API-GW, relay)
                String osName = (String) apiHost.get("os_name");
                if (osName == null || !osName.toLowerCase().startsWith("windows")) {
                    continue;
                }

                Optional<Host> existing = hostRepository.findByAspiaHostId(aspiaHostId);
                if (existing.isPresent()) {
                    Host host = existing.get();
                    updateBasicFields(host, apiHost);
                    host.setOnline(true);
                    if (!Objects.equals(host.getSessionId(), sessionId)) {
                        host.setSessionId(sessionId);
                        host.setNeedsFullSync(true);
                        log.info("Хост {} ({}): session_id изменился, требуется полная синхронизация",
                                host.getComputerName(), aspiaHostId);
                    }
                    host.setLastSyncAt(LocalDateTime.now());
                    hostRepository.save(host);
                } else {
                    Host host = new Host();
                    host.setAspiaHostId(aspiaHostId);
                    host.setSessionId(sessionId);
                    updateBasicFields(host, apiHost);
                    host.setOnline(true);
                    host.setNeedsFullSync(true);
                    host.setLastSyncAt(LocalDateTime.now());
                    hostRepository.save(host);
                    log.info("Новый хост: {} ({})", host.getComputerName(), aspiaHostId);
                }
                updated++;
            }

            lastSyncTime = LocalDateTime.now();
            lastSyncStatus = "Успешно: " + updated + " хостов";
            log.info("Синхронизация списка завершена: {} хостов", updated);
            return updated;
        } catch (Exception e) {
            log.error("Ошибка синхронизации списка хостов", e);
            lastSyncStatus = "Ошибка: " + e.getMessage();
            return 0;
        } finally {
            syncing = false;
        }
    }

    /**
     * Принудительная синхронизация конкретного хоста.
     */
    @Transactional
    public void forceSyncHost(Long hostId) {
        Host host = hostRepository.findById(hostId).orElse(null);
        if (host == null) {
            log.warn("forceSyncHost: хост с id={} не найден", hostId);
            return;
        }
        log.info("Принудительная синхронизация хоста {} ({})", host.getComputerName(), host.getAspiaHostId());
        host.setNeedsFullSync(true);
        hostRepository.save(host);
        fetchAndCompareConfig(host);
    }

    /**
     * Полная синхронизация по расписанию: список + конфигурации изменённых хостов.
     */
    @Scheduled(fixedDelayString = "${aspia.sync.interval:300000}", initialDelay = 60000)
    public void scheduledSync() {
        log.info("Запуск плановой синхронизации...");
        syncHostList();
        syncPendingConfigs();
    }

    // /**
    //  * В рамках исправления БД, метод удаляет из БД хосты, которые не являются Windows (служебные Linux-хосты).
    //  */
    // private void cleanupNonWindowsHosts() {
    //     List<Host> toRemove = new ArrayList<>();
    //     toRemove.addAll(hostRepository.findByOsNameIsNull());
    //     toRemove.addAll(hostRepository.findByOsNameNotLikeIgnoreCase("Windows%"));
    //     if (!toRemove.isEmpty()) {
    //         for (Host host : toRemove) {
    //             softwareRepository.deleteByHost(host);
    //             log.info("Удалён не-Windows хост: {} (OS: {})", host.getComputerName(), host.getOsName());
    //         }
    //         hostRepository.deleteAll(toRemove);
    //         log.info("Удалено {} не-Windows хостов", toRemove.size());
    //     }
    // }

    /**
     * Синхронизация конфигураций хостов, у которых needsFullSync = true.
     * Выполняется параллельно в 5 потоков.
     */
    public void syncPendingConfigs() {
        List<Host> pending = new ArrayList<>(hostRepository.findByNeedsFullSyncTrue());
        // Также синхронизируем хосты без сохранённой конфигурации
        for (Host host : hostRepository.findAll()) {
            if (host.getConfigJson() == null && !host.isNeedsFullSync()) {
                pending.add(host);
            }
        }
        // Фильтруем хосты без aspiaHostId
        pending.removeIf(host -> {
            if (host.getAspiaHostId() == null) {
                log.warn("Пропуск хоста с null aspiaHostId: id={}", host.getId());
                return true;
            }
            return false;
        });
        log.info("Хостов для полной синхронизации: {} (потоков: {})", pending.size(), syncThreads);
        if (pending.isEmpty()) return;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Host host : pending) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        // Перечитываем хост в контексте этой транзакции
                        Host freshHost = hostRepository.findById(host.getId()).orElse(null);
                        if (freshHost != null) {
                            fetchAndCompareConfig(freshHost);
                        }
                    });
                } catch (Exception e) {
                    log.error("Ошибка синхронизации конфигурации хоста {} ({})",
                            host.getComputerName(), host.getAspiaHostId(), e);
                }
            }, syncExecutor);
            futures.add(future);
        }

        // Ждём завершения всех задач
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(syncTimeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Параллельная синхронизация прервана");
        } catch (ExecutionException | TimeoutException e) {
            log.error("Ошибка при параллельной синхронизации: {}", e.getMessage());
        }
    }

    /**
     * Получение и сравнение полной конфигурации хоста.
     */
    @Transactional
    public void fetchAndCompareConfig(Host host) {
        log.info("Получение конфигурации хоста {} ({})...", host.getComputerName(), host.getAspiaHostId());

        Map<String, Object> config = fetchHostConfigFromApi(host);
        if (config == null) {
            log.warn("Не удалось получить конфигурацию хоста {}", host.getAspiaHostId());
            host.setSyncError("API недоступен");
            hostRepository.save(host);
            return;
        }

        // Проверяем, не вернула ли API ошибку
        if (config.containsKey("error")) {
            String errorMsg = (String) config.get("error");
            String errorCode = config.get("code") != null ? (String) config.get("code") : "";

            // PEER_NOT_FOUND — хост просто оффлайн, это не ошибка синхронизации
            if (errorMsg != null && errorMsg.contains("PEER_NOT_FOUND")) {
                log.info("Хост {} ({}) недоступен (оффлайн), пропуск синхронизации",
                        host.getComputerName(), host.getAspiaHostId());
                host.setSyncError(null);
                host.setNeedsFullSync(true);
                hostRepository.save(host);
                return;
            }

            log.warn("Ошибка синхронизации хоста {} ({}): {} [{}]",
                    host.getComputerName(), host.getAspiaHostId(), errorMsg, errorCode);
            host.setSyncError(errorMsg);
            hostRepository.save(host);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> systemInfo = (Map<String, Object>) config.get("system_info");
        if (systemInfo == null) {
            log.warn("system_info отсутствует для хоста {}", host.getAspiaHostId());
            return;
        }

        // Извлекаем нормализованные значения
        String newCpu = extractCpuModel(systemInfo);
        Long newRam = extractTotalRam(systemInfo);
        Long newDisk = extractTotalDisk(systemInfo);
        String newVideo = extractVideoAdapter(systemInfo);
        String newMotherboard = extractMotherboard(systemInfo);
        List<SoftwareInfo> newSoftware = extractSoftwareList(systemInfo);

        boolean isFirstSync = (host.getCpuModel() == null && host.getTotalRamBytes() == null);

        // Сравнение и фиксация изменений (только если это не первая синхронизация)
        List<ComponentChange> detectedChanges = new ArrayList<>();
        if (!isFirstSync) {
            ComponentChange c;
            if (host.isComponentTracked("PROCESSOR")) {
                c = compareAndRecord(host, "PROCESSOR", host.getCpuModel(), newCpu);
                if (c != null) detectedChanges.add(c);
            }
            if (host.isComponentTracked("MEMORY")) {
                c = compareAndRecord(host, "MEMORY", formatBytes(host.getTotalRamBytes()), formatBytes(newRam));
                if (c != null) detectedChanges.add(c);
            }
            if (host.isComponentTracked("DISK")) {
                c = compareAndRecord(host, "DISK", formatBytes(host.getTotalDiskBytes()), formatBytes(newDisk));
                if (c != null) detectedChanges.add(c);
            }
            if (host.isComponentTracked("VIDEO_ADAPTER")) {
                c = compareAndRecord(host, "VIDEO_ADAPTER", host.getVideoAdapter(), newVideo);
                if (c != null) detectedChanges.add(c);
            }
        }

        // Обновляем поля хоста
        host.setCpuModel(newCpu);
        host.setTotalRamBytes(newRam);
        host.setTotalDiskBytes(newDisk);
        host.setVideoAdapter(newVideo);
        host.setMotherboard(newMotherboard);
        host.setNeedsFullSync(false);
        host.setSyncError(null);
        host.setLastSyncAt(LocalDateTime.now());

        // Сохраняем полный JSON system_info для детальной страницы
        try {
            host.setConfigJson(objectMapper.writeValueAsString(systemInfo));
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать system_info для хоста {}", host.getAspiaHostId());
        }

        hostRepository.save(host);

        // Синхронизация списка ПО
        List<ComponentChange> softwareChanges = syncSoftwareList(host, newSoftware, isFirstSync);
        detectedChanges.addAll(softwareChanges);

        // Отправка Telegram-уведомления при наличии изменений
        if (!detectedChanges.isEmpty()) {
            String displayName = host.getDisplayName();
            List<TelegramNotificationService.ChangeInfo> infos = new ArrayList<>();
            for (ComponentChange ch : detectedChanges) {
                infos.add(new TelegramNotificationService.ChangeInfo(
                        displayName, ch.getComponentType(), ch.getChangeType(), ch.getOldValue(), ch.getNewValue()));
            }
            telegramService.notifyChangesDetected(displayName, infos);
        }

        log.info("Конфигурация хоста {} ({}) синхронизирована", host.getComputerName(), host.getAspiaHostId());
    }

    // ========== Извлечение данных из JSON ==========

    @SuppressWarnings("unchecked")
    private String extractCpuModel(Map<String, Object> systemInfo) {
        Map<String, Object> proc = (Map<String, Object>) systemInfo.get("processor");
        if (proc == null) return null;
        return (String) proc.get("model");
    }

    @SuppressWarnings("unchecked")
    private Long extractTotalRam(Map<String, Object> systemInfo) {
        Map<String, Object> memory = (Map<String, Object>) systemInfo.get("memory");
        if (memory == null) return null;
        List<Map<String, Object>> modules = (List<Map<String, Object>>) memory.get("module");
        if (modules == null) return null;

        long total = 0;
        for (Map<String, Object> module : modules) {
            Boolean present = (Boolean) module.get("present");
            if (Boolean.TRUE.equals(present)) {
                Long size = toLong(module.get("size"));
                if (size != null) total += size;
            }
        }
        return total > 0 ? total : null;
    }

    @SuppressWarnings("unchecked")
    private Long extractTotalDisk(Map<String, Object> systemInfo) {
        Map<String, Object> drives = (Map<String, Object>) systemInfo.get("logical_drives");
        if (drives == null) return null;
        List<Map<String, Object>> driveList = (List<Map<String, Object>>) drives.get("drive");
        if (driveList == null) return null;

        long total = 0;
        for (Map<String, Object> drive : driveList) {
            Long size = toLong(drive.get("total_size"));
            if (size != null) total += size;
        }
        return total > 0 ? total : null;
    }

    @SuppressWarnings("unchecked")
    private String extractMotherboard(Map<String, Object> systemInfo) {
        Map<String, Object> mb = (Map<String, Object>) systemInfo.get("motherboard");
        if (mb == null) return null;
        String manufacturer = (String) mb.get("manufacturer");
        String model = (String) mb.get("model");
        if (manufacturer == null && model == null) return null;
        return ((manufacturer != null ? manufacturer : "") + " " + (model != null ? model : "")).trim();
    }

    @SuppressWarnings("unchecked")
    private String extractVideoAdapter(Map<String, Object> systemInfo) {
        Map<String, Object> adapters = (Map<String, Object>) systemInfo.get("video_adapters");
        if (adapters == null) return null;
        List<Map<String, Object>> adapterList = (List<Map<String, Object>>) adapters.get("adapter");
        if (adapterList == null || adapterList.isEmpty()) return null;
        return (String) adapterList.get(0).get("description");
    }

    @SuppressWarnings("unchecked")
    private List<SoftwareInfo> extractSoftwareList(Map<String, Object> systemInfo) {
        Map<String, Object> apps = (Map<String, Object>) systemInfo.get("applications");
        if (apps == null) return Collections.emptyList();
        List<Map<String, Object>> appList = (List<Map<String, Object>>) apps.get("application");
        if (appList == null) return Collections.emptyList();

        List<SoftwareInfo> result = new ArrayList<>();
        for (Map<String, Object> app : appList) {
            String name = (String) app.get("name");
            if (name == null || name.isBlank()) continue;
            result.add(new SoftwareInfo(
                    name,
                    (String) app.get("version"),
                    (String) app.get("publisher"),
                    (String) app.get("install_date")
            ));
        }
        return result;
    }

    // ========== Сравнение и запись изменений ==========

    private ComponentChange compareAndRecord(Host host, String componentType, String oldValue, String newValue) {
        String safeOld = oldValue != null ? oldValue.trim() : "";
        String safeNew = newValue != null ? newValue.trim() : "";
        if (safeOld.equals(safeNew)) return null;

        String changeType = safeOld.isEmpty() ? "ADDED" : (safeNew.isEmpty() ? "REMOVED" : "MODIFIED");
        ComponentChange change = changeRepository.save(new ComponentChange(host, componentType, changeType, oldValue, newValue));
        log.info("Изменение [{}] на хосте {}: '{}' → '{}'",
                componentType, host.getComputerName(), oldValue, newValue);
        return change;
    }

    @Transactional
    public List<ComponentChange> syncSoftwareList(Host host, List<SoftwareInfo> newSoftware, boolean isFirstSync) {
        List<HostSoftware> existingSoftware = softwareRepository.findByHost(host);
        List<ComponentChange> softwareChanges = new ArrayList<>();

        boolean trackSoftware = host.isComponentTracked("SOFTWARE");

        if (!isFirstSync && trackSoftware) {
            // Загружаем исключения ПО из отслеживания
            Set<String> globalExclusions = exclusionRepository.findByHostIsNull().stream()
                    .map(SoftwareExclusion::getSoftwareName).collect(Collectors.toSet());
            Set<String> hostExclusions = exclusionRepository.findByHost(host).stream()
                    .map(SoftwareExclusion::getSoftwareName).collect(Collectors.toSet());

            // Создаём карты для сравнения по ключу name|version
            Map<String, HostSoftware> existingMap = existingSoftware.stream()
                    .collect(Collectors.toMap(HostSoftware::getSoftwareKey, s -> s, (a, b) -> a));
            Map<String, SoftwareInfo> newMap = newSoftware.stream()
                    .collect(Collectors.toMap(SoftwareInfo::getKey, s -> s, (a, b) -> a));

            // Найти удалённое ПО
            for (Map.Entry<String, HostSoftware> entry : existingMap.entrySet()) {
                if (!newMap.containsKey(entry.getKey())) {
                    HostSoftware removed = entry.getValue();
                    if (globalExclusions.contains(removed.getName()) || hostExclusions.contains(removed.getName())) continue;
                    ComponentChange change = changeRepository.save(new ComponentChange(host, "SOFTWARE", "REMOVED",
                            removed.getName() + " " + (removed.getVersion() != null ? removed.getVersion() : ""), ""));
                    softwareChanges.add(change);
                    log.info("ПО удалено на {}: {} {}", host.getComputerName(), removed.getName(), removed.getVersion());
                }
            }

            // Найти добавленное ПО
            for (Map.Entry<String, SoftwareInfo> entry : newMap.entrySet()) {
                if (!existingMap.containsKey(entry.getKey())) {
                    SoftwareInfo added = entry.getValue();
                    if (globalExclusions.contains(added.name) || hostExclusions.contains(added.name)) continue;
                    ComponentChange change = changeRepository.save(new ComponentChange(host, "SOFTWARE", "ADDED",
                            "", added.name + " " + (added.version != null ? added.version : "")));
                    softwareChanges.add(change);
                    log.info("ПО добавлено на {}: {} {}", host.getComputerName(), added.name, added.version);
                }
            }
        }

        // Перезаписываем список ПО
        softwareRepository.deleteByHost(host);
        for (SoftwareInfo sw : newSoftware) {
            softwareRepository.save(new HostSoftware(host, sw.name, sw.version, sw.publisher, sw.installDate));
        }
        return softwareChanges;
    }

    // ========== API вызовы ==========

    private List<Map<String, Object>> fetchHostListFromApi() {
        try {
            String url = apiBaseUrl + "/hosts";
            ResponseEntity<List<Map<String, Object>>> response = aspiaRestTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody();
        } catch (Exception e) {
            log.error("Ошибка вызова API /hosts: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> fetchHostConfigFromApi(Host host) {
        Integer aspiaHostId = host.getAspiaHostId();
        try {
            String url = apiBaseUrl + "/hosts/" + aspiaHostId + "/config?category=all";

            HttpEntity<?> requestEntity = null;
            if (host.getAspiaHostUser() != null && !host.getAspiaHostUser().isEmpty()
                    && host.getAspiaHostPasswordEncrypted() != null && !host.getAspiaHostPasswordEncrypted().isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Aspia-Host-User", host.getAspiaHostUser());
                headers.set("X-Aspia-Host-Password", CryptoUtils.decrypt(host.getAspiaHostPasswordEncrypted(), encryptionKey));
                requestEntity = new HttpEntity<>(headers);
                log.debug("Отправка запроса конфигурации хоста {} с учётными данными", aspiaHostId);
            }

            ResponseEntity<Map<String, Object>> response = aspiaRestTemplate.exchange(
                    url, HttpMethod.GET, requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("HTTP ошибка при вызове API /hosts/{}/config: {} {}", aspiaHostId, e.getStatusCode(), e.getMessage());
            try {
                Map<String, Object> errorBody = objectMapper.readValue(
                        e.getResponseBodyAsString(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                return errorBody;
            } catch (Exception parseEx) {
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("error", "HTTP " + e.getStatusCode() + ": " + e.getStatusText());
                fallback.put("code", "http_error");
                return fallback;
            }
        } catch (Exception e) {
            log.error("Ошибка вызова API /hosts/{}/config: {}", aspiaHostId, e.getMessage());
            return null;
        }
    }

    // ========== Вспомогательные методы ==========

    private void updateBasicFields(Host host, Map<String, Object> apiHost) {
        host.setComputerName((String) apiHost.get("computer_name"));
        host.setIpAddress((String) apiHost.get("ip_address"));
        host.setOsName((String) apiHost.get("os_name"));
        host.setArchitecture((String) apiHost.get("architecture"));
        host.setAspiaVersion((String) apiHost.get("version"));
    }

    private static Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static String formatBytes(Long bytes) {
        if (bytes == null) return "";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.1f GB", gb);
    }

    // ========== Вложенные классы ==========

    private static class SoftwareInfo {
        final String name;
        final String version;
        final String publisher;
        final String installDate;

        SoftwareInfo(String name, String version, String publisher, String installDate) {
            this.name = name;
            this.version = version;
            this.publisher = publisher;
            this.installDate = installDate;
        }

        String getKey() {
            return name + "|" + (version != null ? version : "");
        }
    }
}
