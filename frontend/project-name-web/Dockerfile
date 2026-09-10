# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-07-29
FROM node:22-alpine AS build
# 同 ai 镜像：registry 可选，**默认走上游**。写死成 npmmirror 是「在中国构建」
# 的优化，而构建实际发生在 GitHub 的 runner 上。中国本地构建传：
#   --build-arg NPM_REGISTRY=https://registry.npmmirror.com
ARG NPM_REGISTRY="https://registry.npmjs.org"
WORKDIR /workspace
RUN corepack enable
COPY frontend/project-name-web/package.json frontend/project-name-web/pnpm-lock.yaml frontend/project-name-web/pnpm-workspace.yaml ./
RUN --mount=type=secret,id=github_packages_token,required=true \
    --mount=type=cache,target=/root/.local/share/pnpm/store \
    export NPM_CONFIG_USERCONFIG=/tmp/npmrc \
    && pnpm config set registry "$NPM_REGISTRY" \
    && pnpm config set "@vxture:registry" https://npm.pkg.github.com \
    && pnpm config set "//npm.pkg.github.com/:_authToken" "$(cat /run/secrets/github_packages_token)" \
    && pnpm install --frozen-lockfile \
    && rm -f /tmp/npmrc
COPY frontend/project-name-web/ ./
RUN pnpm build:prod

FROM nginx:1.27-alpine
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
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
