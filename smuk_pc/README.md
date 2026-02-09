# СМУК ПК — Сборка и запуск в Docker

## Требования

- Docker/Podman
- Доступ к PostgreSQL
- Доступ к Aspia API GW

## Быстрый старт

### 1. Сборка образа

```bash
cd source_git
docker build -t smuk-pc .
```

### 2. Запуск контейнера

**С параметрами по умолчанию** (БД и API уже прописаны в конфигурации):

```bash
docker run -d \
  --name smuk-pc \
  -p 8082:8082 \
  smuk-pc
```

**С переопределением параметров через переменные окружения:**

```bash
docker run -d \
  --name smuk-pc \
  -p 8082:8082 \
  -e DB_URL=jdbc:postgresql://host:5432/mydb \
  -e DB_USER=myuser \
  -e DB_PASSWORD=mypassword \
  -e ASPIA_API_URL=http://aspia-host:8080 \
  -e TELEGRAM_ENABLED=false \
  smuk-pc
```

### 3. Открыть в браузере

```
http://localhost:8082
```

Логин по умолчанию: `admin` / `admin`

## Переменные окружения

| Переменная           | Описание                               | По умолчанию                              |
|----------------------|----------------------------------------|-------------------------------------------|
| `DB_URL`             | JDBC URL базы данных                   | `jdbc:postgresql://localhost:5432/smuk_db` |
| `DB_USER`            | Пользователь БД                        | `postgres`                                |
| `DB_PASSWORD`        | Пароль БД                              | *(требуется задать)*                      |
| `ASPIA_API_URL`      | URL Aspia API GW                       | `http://localhost:8080`                   |
| `ENCRYPTION_KEY`     | Ключ шифрования (AES-128, 16 символов) | *(требуется задать)*                      |
| `TELEGRAM_ENABLED`   | Включить Telegram-уведомления          | `false`                                   |
| `TELEGRAM_BOT_TOKEN` | Токен Telegram-бота                    | *(требуется задать, если enabled=true)*   |
| `TELEGRAM_CHAT_ID`   | ID чата для уведомлений                | *(требуется задать, если enabled=true)*   |

## Управление контейнером

```bash
# Просмотр логов
docker logs -f smuk-pc

# Остановка
docker stop smuk-pc

# Запуск после остановки
docker start smuk-pc

# Удаление контейнера
docker rm -f smuk-pc

# Пересборка после изменений
docker build -t smuk-pc . && docker rm -f smuk-pc && docker run -d --name smuk-pc -p 8082:8082 smuk-pc
```

## Структура проекта

```
source_git/
├── Dockerfile                           # Многоэтапная сборка (Maven + JRE)
├── pom.xml                              # Зависимости Maven
├── README.md                            # Эта инструкция
└── src/
    └── main/
        ├── java/com/aspia/inventory/    # Java-код приложения
        │   ├── Application.java         # Главный класс
        │   ├── config/                  # Конфигурация (Security, API, DataLoader)
        │   ├── controller/              # Контроллеры (Dashboard, Host, Inventory, ...)
        │   ├── model/                   # JPA-сущности (Host, HostSoftware, ...)
        │   ├── repository/              # Spring Data репозитории
        │   ├── service/                 # Бизнес-логика (синхронизация, экспорт, Telegram)
        │   └── util/                    # Утилиты (шифрование)
        └── resources/
            ├── application.properties   # Настройки приложения
            ├── static/css/              # CSS-стили
            └── templates/               # Thymeleaf-шаблоны
```

