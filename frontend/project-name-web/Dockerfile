# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-07-29
FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable
COPY frontend/project-name-web/package.json frontend/project-name-web/pnpm-lock.yaml frontend/project-name-web/pnpm-workspace.yaml ./
RUN --mount=type=secret,id=github_packages_token,required=true \
    --mount=type=cache,target=/root/.local/share/pnpm/store \
    export NPM_CONFIG_USERCONFIG=/tmp/npmrc \
    && pnpm config set registry https://registry.npmmirror.com \
    && pnpm config set "@vxture:registry" https://npm.pkg.github.com \
    && pnpm config set "//npm.pkg.github.com/:_authToken" "$(cat /run/secrets/github_packages_token)" \
    && pnpm install --frozen-lockfile \
    && rm -f /tmp/npmrc
COPY frontend/project-name-web/ ./
RUN pnpm build:prod

FROM nginx:1.27-alpine
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
