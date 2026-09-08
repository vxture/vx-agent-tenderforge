# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-07-29
import asyncio
import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from czghagent_ai.api.internal import create_ai_provider
from czghagent_ai.api.internal import router as internal_router
from czghagent_ai.services.ai_provider import AiProviderNotConfiguredError

app = FastAPI(
    title="TenderAgent 文档解析服务",
    version="1.0.0",
    docs_url="/docs",
    redoc_url=None,
)
app.include_router(internal_router)
logger = logging.getLogger("czghagent_ai.validation")


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exception: RequestValidationError) -> JSONResponse:
    logger.warning("request_validation_failed path=%s errors=%s", request.url.path, exception.errors())
    return JSONResponse(status_code=422, content={"detail": exception.errors()})


@app.get("/health")
async def health() -> JSONResponse:
    return JSONResponse({"status": "UP", "service": "tenderagent-parser"})


@app.get("/ready")
async def ready() -> JSONResponse:
    try:
        await asyncio.to_thread(create_ai_provider().validate_configuration)
    except AiProviderNotConfiguredError:
        return JSONResponse(
            status_code=503,
            content={
                "status": "DOWN",
                "service": "tenderagent-parser",
                "code": AiProviderNotConfiguredError.code,
            },
        )
    return JSONResponse({"status": "UP", "service": "tenderagent-parser"})
