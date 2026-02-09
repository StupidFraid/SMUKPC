package com.aspia.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot%s/sendMessage";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Value("${telegram.bot.enabled:false}")
    private boolean enabled;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.chat-id:}")
    private String chatId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * DTO для передачи данных об изменении без привязки к Hibernate-сессии.
     */
    public static class ChangeInfo {
        public final String hostName;
        public final String componentType;
        public final String changeType;
        public final String oldValue;
        public final String newValue;

        public ChangeInfo(String hostName, String componentType, String changeType, String oldValue, String newValue) {
            this.hostName = hostName;
            this.componentType = componentType;
            this.changeType = changeType;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    @Async
    public void notifyChangesDetected(String hostName, List<ChangeInfo> changes) {
        if (!isConfigured() || changes.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDD14 *Изменение конфигурации*\n\n");
        sb.append("\uD83D\uDDA5 *Хост:* ").append(escapeMarkdown(hostName)).append("\n\n");

        for (ChangeInfo change : changes) {
            sb.append(getComponentEmoji(change.componentType));
            sb.append(" *Компонент:* ").append(escapeMarkdown(change.componentType)).append("\n");
            sb.append("\uD83D\uDD04 *Тип:* ").append(escapeMarkdown(formatChangeType(change.changeType))).append("\n");

            if (change.oldValue != null && !change.oldValue.isEmpty()) {
                sb.append("   _Было:_ ").append(escapeMarkdown(change.oldValue)).append("\n");
            }
            if (change.newValue != null && !change.newValue.isEmpty()) {
                sb.append("   _Стало:_ ").append(escapeMarkdown(change.newValue)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("\uD83D\uDCC5 ").append(escapeMarkdown(LocalDateTime.now().format(DATE_FMT)));

        sendMessage(sb.toString());
    }

    @Async
    public void notifyChangesAcknowledged(String adminName, String hostName, List<ChangeInfo> changes) {
        if (!isConfigured() || changes.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\u2705 *Изменения подтверждены*\n\n");
        sb.append("\uD83D\uDC64 *Администратор:* ").append(escapeMarkdown(adminName)).append("\n");
        sb.append("\uD83D\uDDA5 *Хост:* ").append(escapeMarkdown(hostName)).append("\n");
        sb.append("\uD83D\uDCCB *Подтверждено:* ").append(changes.size()).append(" изм\\.\n\n");

        for (ChangeInfo change : changes) {
            sb.append(getComponentEmoji(change.componentType));
            sb.append(" ").append(escapeMarkdown(change.componentType));
            sb.append(" \\— ").append(escapeMarkdown(formatChangeType(change.changeType)));
            if (change.oldValue != null && !change.oldValue.isEmpty()) {
                sb.append(": ").append(escapeMarkdown(change.oldValue));
            }
            if (change.newValue != null && !change.newValue.isEmpty()) {
                sb.append(" \\→ ").append(escapeMarkdown(change.newValue));
            }
            sb.append("\n");
        }

        sb.append("\n\uD83D\uDCC5 ").append(escapeMarkdown(LocalDateTime.now().format(DATE_FMT)));
        sendMessage(sb.toString());
    }

    @Async
    public void notifyAllChangesAcknowledged(String adminName, List<ChangeInfo> changes) {
        if (!isConfigured() || changes.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\u2705 *Все изменения подтверждены*\n\n");
        sb.append("\uD83D\uDC64 *Администратор:* ").append(escapeMarkdown(adminName)).append("\n");
        sb.append("\uD83D\uDCCB *Подтверждено:* ").append(changes.size()).append(" изм\\.\n\n");

        // Группируем по хосту
        Map<String, List<ChangeInfo>> byHost = new LinkedHashMap<>();
        for (ChangeInfo c : changes) {
            byHost.computeIfAbsent(c.hostName, k -> new ArrayList<>()).add(c);
        }

        for (Map.Entry<String, List<ChangeInfo>> entry : byHost.entrySet()) {
            sb.append("\uD83D\uDDA5 *").append(escapeMarkdown(entry.getKey())).append(":*\n");
            for (ChangeInfo change : entry.getValue()) {
                sb.append("  ").append(getComponentEmoji(change.componentType));
                sb.append(" ").append(escapeMarkdown(change.componentType));
                sb.append(" \\— ").append(escapeMarkdown(formatChangeType(change.changeType)));
                if (change.oldValue != null && !change.oldValue.isEmpty()) {
                    sb.append(": ").append(escapeMarkdown(change.oldValue));
                }
                if (change.newValue != null && !change.newValue.isEmpty()) {
                    sb.append(" \\→ ").append(escapeMarkdown(change.newValue));
                }
                sb.append("\n");
            }
        }

        sb.append("\n\uD83D\uDCC5 ").append(escapeMarkdown(LocalDateTime.now().format(DATE_FMT)));
        sendMessage(sb.toString());
    }

    private boolean isConfigured() {
        if (!enabled) return false;
        if (botToken == null || botToken.isEmpty()) {
            log.warn("Telegram bot token не настроен");
            return false;
        }
        if (chatId == null || chatId.isEmpty()) {
            log.warn("Telegram chat ID не настроен");
            return false;
        }
        return true;
    }

    private void sendMessage(String text) {
        try {
            String url = String.format(TELEGRAM_API_URL, botToken);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "MarkdownV2");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);

            log.debug("Telegram уведомление отправлено");
        } catch (Exception e) {
            log.error("Ошибка отправки Telegram уведомления: {}", e.getMessage());
        }
    }

    private static String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!])", "\\\\$1");
    }

    private static String getComponentEmoji(String componentType) {
        if (componentType == null) return "\uD83D\uDD27";
        switch (componentType) {
            case "PROCESSOR": return "\uD83E\uDDE0";
            case "MEMORY": return "\uD83D\uDCBE";
            case "DISK": return "\uD83D\uDCBF";
            case "VIDEO_ADAPTER": return "\uD83C\uDFA8";
            case "SOFTWARE": return "\uD83D\uDCE6";
            default: return "\uD83D\uDD27";
        }
    }

    private static String formatChangeType(String changeType) {
        if (changeType == null) return "Неизвестно";
        switch (changeType) {
            case "ADDED": return "Добавлено";
            case "REMOVED": return "Удалено";
            case "MODIFIED": return "Изменено";
            default: return changeType;
        }
    }
}
