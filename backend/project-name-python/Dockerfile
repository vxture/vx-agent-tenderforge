# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-07-29
FROM python:3.12-slim
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
# 镜像源可选，**默认走上游**。这两处一度写死成阿里云——那是「在中国构建」的
# 优化，但镜像是在 GitHub 的 runner 上构建的（美/欧），从那儿拉阿里云恰好是
# 最慢的方向。一次 CI 构建因此在 apt 上耗掉 23 分钟、被 30 分钟超时掐断，而
# 失败显示成 cancelled，从结论上看不出是网络。
#
# 在中国本地构建时按需传入：
#   --build-arg APT_MIRROR=https://mirrors.aliyun.com
#   --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ARG APT_MIRROR=""
ARG PIP_INDEX_URL="https://pypi.org/simple/"
RUN if [ -n "$APT_MIRROR" ]; then \
        # 用 -E 与协议无关的 [a-z]+:// 匹配：基础镜像里那份 sources 用的是明文
        # http，而替换目标带自己的 scheme。写死 http:// 既多一个假设，也会被
        # 扫描器当成「这里在用明文协议」——它看不出那是被替换掉的那一侧。
        sed -i -E \
            -e "s|[a-z]+://deb[.]debian[.]org/debian-security|${APT_MIRROR}/debian-security|g" \
            -e "s|[a-z]+://deb[.]debian[.]org/debian|${APT_MIRROR}/debian|g" \
            /etc/apt/sources.list.d/debian.sources; \
    fi \
    && apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install -y --no-install-recommends \
        antiword fonts-noto-cjk libreoffice-writer poppler-utils \
        tesseract-ocr tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY backend/project-name-python/requirements.txt ./requirements.txt
RUN pip install \
        --no-cache-dir \
        --retries 5 \
        --timeout 120 \
        --index-url "$PIP_INDEX_URL" \
        -r requirements.txt
COPY backend/project-name-python/fontconfig/local.conf /etc/fonts/local.conf
RUN fc-cache -f \
    && test "$(fc-match -f '%{family[0]}' SimSun)" = "Noto Serif CJK SC"
COPY backend/project-name-python/czghagent_ai ./czghagent_ai
COPY backend/project-name-python/main.py ./main.py
USER app
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
