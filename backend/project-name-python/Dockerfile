# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-07-29
FROM python:3.12-slim
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
