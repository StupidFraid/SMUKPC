# --- Этап 1: Сборка ---
FROM maven:3.9-eclipse-temurin-11 AS build

WORKDIR /app

# Кэшируем зависимости — копируем только pom.xml и скачиваем зависимости
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходники и собираем JAR
COPY src ./src
RUN mvn package -DskipTests -B

# --- Этап 2: Запуск ---
FROM eclipse-temurin:11-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
