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
# 构建期身份注入（治理规范 025 §4.1）。四个 ARG 都带**诚实默认值**：不传也能
# 构建，值落 dev/unknown/local 而不是假装成某个版本。liveness 端点只读这些，
# 不再自己编——`sha:"dev"` 那类事故的根治点就在这里。
#
# DEPLOY_STAGE 的默认取 local 而不是规范示例里的 dev：本仓同一个镜像也用于
# 本地开发，而 dev 在本产品的 DeployStage 里已经算「部署态」，会触发
# 「拒绝以替身启动」那道闸门——默认成 dev 等于本地栈起不来。
#
# 而且 DEPLOY_STAGE 在运行时仍可被 compose 覆盖，这偏离 025 §4「stage 必须
# 构建期注入」，已登记为 **TD-005**（详细设计 §13b）。缓解手段是 compose 里
# 写 ${DEPLOY_STAGE:?} 而非 :-local——**没有默认值**，漏配直接拒绝启动，
# 不会静默落到唯一放行替身的那一档。另外三个溯源值不受此条影响，严格只读镜像 ENV。
ARG APP_VERSION=dev
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
ARG DEPLOY_STAGE=local
ENV APP_VERSION=${APP_VERSION}
ENV GIT_SHA=${GIT_SHA}
ENV BUILD_TIME=${BUILD_TIME}
ENV DEPLOY_STAGE=${DEPLOY_STAGE}
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
