# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-07-29
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY backend/project-name-java/ ./
# Tests run in CI/local verification; image builds must package the production
# modules even when an unrelated test source is not part of the runtime image.
RUN --mount=type=cache,target=/root/.m2 mvn -q -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/project-name-start/target/czghagent-start-1.0.0-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/data/private && chown -R app:app /app
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
