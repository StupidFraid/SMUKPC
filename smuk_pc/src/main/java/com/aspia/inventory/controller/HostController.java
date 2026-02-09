package com.aspia.inventory.controller;

import com.aspia.inventory.model.ComponentChange;
import com.aspia.inventory.model.Host;
import com.aspia.inventory.model.HostGroup;
import com.aspia.inventory.model.HostSoftware;
import com.aspia.inventory.model.SoftwareExclusion;
import com.aspia.inventory.repository.ComponentChangeRepository;
import com.aspia.inventory.repository.HostGroupRepository;
import com.aspia.inventory.repository.HostRepository;
import com.aspia.inventory.repository.HostSoftwareRepository;
import com.aspia.inventory.repository.SoftwareExclusionRepository;
import com.aspia.inventory.service.AspiaSyncService;
import com.aspia.inventory.service.InventoryExportService;
import com.aspia.inventory.service.TelegramNotificationService;
import com.aspia.inventory.util.CryptoUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HostController {

    private static final Logger log = LoggerFactory.getLogger(HostController.class);

    private final HostRepository hostRepository;
    private final HostSoftwareRepository softwareRepository;
    private final ComponentChangeRepository changeRepository;
    private final HostGroupRepository groupRepository;
    private final SoftwareExclusionRepository exclusionRepository;
    private final AspiaSyncService syncService;
    private final InventoryExportService exportService;
    private final TelegramNotificationService telegramService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.encryption.key}")
    private String encryptionKey;

    public HostController(HostRepository hostRepository,
                          HostSoftwareRepository softwareRepository,
                          ComponentChangeRepository changeRepository,
                          HostGroupRepository groupRepository,
                          SoftwareExclusionRepository exclusionRepository,
                          AspiaSyncService syncService,
                          InventoryExportService exportService,
                          TelegramNotificationService telegramService) {
        this.hostRepository = hostRepository;
        this.softwareRepository = softwareRepository;
        this.changeRepository = changeRepository;
        this.groupRepository = groupRepository;
        this.exclusionRepository = exclusionRepository;
        this.syncService = syncService;
        this.exportService = exportService;
        this.telegramService = telegramService;
    }

    @GetMapping("/hosts")
    public String hosts(@RequestParam(required = false) String filter, Model model) {
        model.addAttribute("currentPage", "hosts");
        List<Host> hosts = hostRepository.findAll();
        model.addAttribute("hosts", hosts);
        long online = hosts.stream().filter(Host::isOnline).count();
        long errorHosts = hosts.stream().filter(h -> h.getSyncError() != null).count();
        model.addAttribute("totalHosts", hosts.size());
        model.addAttribute("onlineHosts", online);
        model.addAttribute("offlineHosts", hosts.size() - online);
        model.addAttribute("errorHosts", errorHosts);

        // ID хостов с изменениями за 24ч — для фильтра "С изменениями"
        Set<Long> changedHostIds = new HashSet<>(
                changeRepository.findDistinctHostIdsWithChangesAfter(LocalDateTime.now().minusHours(24)));
        model.addAttribute("changedHostIds", changedHostIds);
        model.addAttribute("changedHostsCount", changedHostIds.size());

        // Группы для фильтра
        model.addAttribute("groups", groupRepository.findAll());

        // Фильтр из URL (для перехода с дашборда)
        model.addAttribute("filter", filter != null ? filter : "");
        return "hosts";
    }

    @GetMapping("/hosts/{id}")
    public String hostDetail(@PathVariable Long id, Model model) {
        model.addAttribute("currentPage", "hosts");
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) {
            return "redirect:/hosts";
        }
        model.addAttribute("host", host);

        List<HostSoftware> software = softwareRepository.findByHost(host);
        model.addAttribute("software", software);

        // Изменения хоста
        List<ComponentChange> hostChanges = changeRepository.findByHostOrderByDetectedAtDesc(host);
        model.addAttribute("hostChanges", hostChanges);

        // Количество неподтверждённых изменений
        long unacknowledgedCount = changeRepository.findByHostAndAcknowledgedFalse(host).size();
        model.addAttribute("unacknowledgedCount", unacknowledgedCount);

        // Все группы для формы назначения
        model.addAttribute("allGroups", groupRepository.findAll());

        // Настройки отслеживания
        model.addAttribute("allComponentTypes", Host.getAllComponentTypes());
        model.addAttribute("effectiveTracked", host.getEffectiveTrackedComponents());

        // Исключения ПО из отслеживания
        Set<String> globalExcludedSoftware = exclusionRepository.findByHostIsNull().stream()
                .map(SoftwareExclusion::getSoftwareName).collect(Collectors.toSet());
        Set<String> hostExcludedSoftware = exclusionRepository.findByHost(host).stream()
                .map(SoftwareExclusion::getSoftwareName).collect(Collectors.toSet());
        model.addAttribute("globalExcludedSoftware", globalExcludedSoftware);
        model.addAttribute("hostExcludedSoftware", hostExcludedSoftware);

        // Парсинг JSON конфигурации для детальных вкладок
        if (host.getConfigJson() != null) {
            try {
                Map<String, Object> sysInfo = objectMapper.readValue(
                        host.getConfigJson(), new TypeReference<Map<String, Object>>() {});

                model.addAttribute("motherboard", extractMap(sysInfo, "motherboard"));
                model.addAttribute("bios", extractMap(sysInfo, "bios"));
                model.addAttribute("processor", extractMap(sysInfo, "processor"));
                model.addAttribute("memoryModules", extractPresentMemoryModules(sysInfo));
                model.addAttribute("drives", extractList(extractMap(sysInfo, "logical_drives"), "drive"));
                model.addAttribute("videoAdapters", extractList(extractMap(sysInfo, "video_adapters"), "adapter"));
                model.addAttribute("monitors", extractList(extractMap(sysInfo, "monitors"), "monitor"));
                model.addAttribute("networkAdapters", extractNetworkAdapters(sysInfo));
                model.addAttribute("osInfo", extractMap(sysInfo, "operating_system"));
            } catch (Exception e) {
                // Если JSON некорректный — просто не показываем детали
            }
        }

        return "host-detail";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/sync")
    public String syncHost(@PathVariable Long id) {
        syncService.forceSyncHost(id);
        return "redirect:/hosts/" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/alias")
    public String updateHostAlias(@PathVariable Long id, @RequestParam(required = false) String alias) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return "redirect:/hosts";
        host.setAlias(alias != null && !alias.trim().isEmpty() ? alias.trim() : null);
        hostRepository.save(host);
        return "redirect:/hosts/" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/acknowledge")
    public String acknowledgeHostChanges(@PathVariable Long id, java.security.Principal principal) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return "redirect:/hosts";
        List<ComponentChange> unacknowledged = changeRepository.findByHostAndAcknowledgedFalse(host);
        LocalDateTime now = LocalDateTime.now();
        String username = principal.getName();
        for (ComponentChange c : unacknowledged) {
            c.setAcknowledged(true);
            c.setAcknowledgedAt(now);
            c.setAcknowledgedBy(username);
        }
        changeRepository.saveAll(unacknowledged);
        if (!unacknowledged.isEmpty()) {
            String displayName = host.getDisplayName();
            List<TelegramNotificationService.ChangeInfo> infos = new java.util.ArrayList<>();
            for (ComponentChange c : unacknowledged) {
                infos.add(new TelegramNotificationService.ChangeInfo(
                        displayName, c.getComponentType(), c.getChangeType(), c.getOldValue(), c.getNewValue()));
            }
            telegramService.notifyChangesAcknowledged(username, displayName, infos);
        }
        return "redirect:/hosts/" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/delete")
    @org.springframework.transaction.annotation.Transactional
    public String deleteHost(@PathVariable Long id) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return "redirect:/hosts";
        softwareRepository.deleteByHost(host);
        changeRepository.deleteByHost(host);
        host.setGroups(new HashSet<>());
        hostRepository.save(host);
        hostRepository.delete(host);
        return "redirect:/hosts";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/groups")
    public String updateHostGroups(@PathVariable Long id, @RequestParam(required = false) List<Long> groupIds) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return "redirect:/hosts";
        if (groupIds == null || groupIds.isEmpty()) {
            host.setGroups(new HashSet<>());
        } else {
            host.setGroups(new HashSet<>(groupRepository.findAllById(groupIds)));
        }
        hostRepository.save(host);
        return "redirect:/hosts/" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/tracking")
    public String updateHostTracking(@PathVariable Long id,
                                     @RequestParam(required = false) Boolean useOverride,
                                     @RequestParam(required = false) List<String> trackedComponents) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return "redirect:/hosts";
        if (Boolean.TRUE.equals(useOverride) && trackedComponents != null && !trackedComponents.isEmpty()) {
            host.setTrackedComponentsOverride(String.join(",", trackedComponents));
        } else {
            host.setTrackedComponentsOverride(null);
        }
        hostRepository.save(host);
        return "redirect:/hosts/" + id;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/credentials")
    public String updateHostCredentials(@PathVariable Long id,
                                        @RequestParam(required = false) String user,
                                        @RequestParam(required = false) String password) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return "redirect:/hosts";

        host.setAspiaHostUser(user != null && !user.trim().isEmpty() ? user.trim() : null);
        if (password != null && !password.trim().isEmpty()) {
            host.setAspiaHostPasswordEncrypted(CryptoUtils.encrypt(password.trim(), encryptionKey));
        } else if (user == null || user.trim().isEmpty()) {
            // Если логин пуст — сбрасываем и пароль
            host.setAspiaHostPasswordEncrypted(null);
        }
        hostRepository.save(host);
        return "redirect:/hosts/" + id;
    }

    @GetMapping("/hosts/{id}/export/pdf")
    public ResponseEntity<byte[]> exportHostPdf(@PathVariable Long id) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return ResponseEntity.notFound().build();
        try {
            List<HostSoftware> software = softwareRepository.findByHost(host);
            List<ComponentChange> changes = changeRepository.findByHostOrderByDetectedAtDesc(host);
            byte[] data = exportService.exportHostCardPdf(host, software, changes);
            String filename = "host_" + host.getComputerName() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(data);
        } catch (Exception e) {
            log.error("Ошибка экспорта карточки хоста {} в PDF", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/hosts/{id}/export/excel")
    public ResponseEntity<byte[]> exportHostExcel(@PathVariable Long id) {
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) return ResponseEntity.notFound().build();
        try {
            List<HostSoftware> software = softwareRepository.findByHost(host);
            List<ComponentChange> changes = changeRepository.findByHostOrderByDetectedAtDesc(host);
            byte[] data = exportService.exportHostCardExcel(host, software, changes);
            String filename = "host_" + host.getComputerName() + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            log.error("Ошибка экспорта карточки хоста {} в Excel", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/hosts/{id}/software/toggle-tracking")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> toggleHostSoftwareTracking(@PathVariable Long id, @RequestParam String name) {
        Map<String, Object> result = new HashMap<>();
        Host host = hostRepository.findById(id).orElse(null);
        if (host == null) {
            result.put("success", false);
            return result;
        }
        Optional<SoftwareExclusion> existing = exclusionRepository.findBySoftwareNameAndHost(name, host);
        if (existing.isPresent()) {
            exclusionRepository.delete(existing.get());
            result.put("tracked", true);
        } else {
            exclusionRepository.save(new SoftwareExclusion(name, host));
            result.put("tracked", false);
        }
        result.put("success", true);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        if (val instanceof List) return (List<Map<String, Object>>) val;
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractPresentMemoryModules(Map<String, Object> sysInfo) {
        Map<String, Object> memory = extractMap(sysInfo, "memory");
        List<Map<String, Object>> modules = extractList(memory, "module");
        List<Map<String, Object>> present = new ArrayList<>();
        for (Map<String, Object> m : modules) {
            if (Boolean.TRUE.equals(m.get("present"))) {
                present.add(m);
            }
        }
        return present;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractNetworkAdapters(Map<String, Object> sysInfo) {
        Object adapters = sysInfo.get("network_adapters");
        if (!(adapters instanceof Map)) return Collections.emptyList();

        Object list = ((Map<String, Object>) adapters).get("adapter");
        if (!(list instanceof List)) return Collections.emptyList();

        List<Map<String, Object>> all = (List<Map<String, Object>>) list;
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> a : all) {
            Map<String, Object> prepared = new HashMap<>();
            prepared.put("adapterName", a.get("adapter_name"));
            prepared.put("connectionName", a.get("connection_name"));
            prepared.put("mac", a.get("mac"));

            // Конвертируем speed (бит/с строка) → Мбит/с
            Object speedObj = a.get("speed");
            if (speedObj != null) {
                try {
                    long speedBps = Long.parseLong(speedObj.toString());
                    prepared.put("speedMbps", speedBps / 1_000_000);
                } catch (NumberFormatException e) {
                    prepared.put("speedMbps", speedObj);
                }
            }

            // Извлекаем IP и маску из address[0]
            Object addrObj = a.get("address");
            if (addrObj instanceof List) {
                List<Map<String, Object>> addresses = (List<Map<String, Object>>) addrObj;
                if (!addresses.isEmpty()) {
                    Map<String, Object> firstAddr = addresses.get(0);
                    prepared.put("ip", firstAddr.get("ip"));
                    prepared.put("mask", firstAddr.get("mask"));
                }
            }

            // Извлекаем gateway[0]
            Object gwObj = a.get("gateway");
            if (gwObj instanceof List) {
                List<String> gateways = (List<String>) gwObj;
                if (!gateways.isEmpty()) {
                    prepared.put("gateway", gateways.get(0));
                }
            }

            // Фильтруем — показываем только адаптеры с IP
            if (prepared.get("ip") != null) {
                result.add(prepared);
            }
        }

        return result.isEmpty() ? prepareAllAdapters(all) : result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> prepareAllAdapters(List<Map<String, Object>> all) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> a : all) {
            Map<String, Object> prepared = new HashMap<>();
            prepared.put("adapterName", a.get("adapter_name"));
            prepared.put("connectionName", a.get("connection_name"));
            prepared.put("mac", a.get("mac"));
            Object speedObj = a.get("speed");
            if (speedObj != null) {
                try {
                    long speedBps = Long.parseLong(speedObj.toString());
                    prepared.put("speedMbps", speedBps / 1_000_000);
                } catch (NumberFormatException e) {
                    prepared.put("speedMbps", speedObj);
                }
            }
            result.add(prepared);
        }
        return result;
    }
}
