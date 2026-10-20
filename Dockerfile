# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY src ./src

RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw && ./mvnw clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S app && adduser -S -G app app && mkdir -p /app/logs && chown app:app /app/logs

WORKDIR /app
COPY --from=builder /build/target/quarkus-app/lib/ ./lib/
COPY --from=builder /build/target/quarkus-app/*.jar ./
COPY --from=builder /build/target/quarkus-app/app/ ./app/
COPY --from=builder /build/target/quarkus-app/quarkus/ ./quarkus/

USER app

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
