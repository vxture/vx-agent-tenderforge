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
ARG APP_VERSION=dev
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
ARG DEPLOY_STAGE=local
ENV APP_VERSION=${APP_VERSION}
ENV GIT_SHA=${GIT_SHA}
ENV BUILD_TIME=${BUILD_TIME}
ENV DEPLOY_STAGE=${DEPLOY_STAGE}
RUN sed -i \
        -e 's|http://deb.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g' \
        -e 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g' \
        /etc/apt/sources.list.d/debian.sources \
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
        --index-url https://mirrors.aliyun.com/pypi/simple/ \
        -r requirements.txt
COPY backend/project-name-python/fontconfig/local.conf /etc/fonts/local.conf
RUN fc-cache -f \
    && test "$(fc-match -f '%{family[0]}' SimSun)" = "Noto Serif CJK SC"
COPY backend/project-name-python/czghagent_ai ./czghagent_ai
COPY backend/project-name-python/main.py ./main.py
USER app
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
