# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-08
import asyncio
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from czghagent_ai.api.internal import create_ai_provider, describe_model_exit
from czghagent_ai.api.internal import router as internal_router
from czghagent_ai.errors import ServiceError, envelope
from czghagent_ai.services.ai_provider import AiProviderNotConfiguredError
from czghagent_ai.task_context import TaskIdMiddleware

@asynccontextmanager
async def _lifespan(_: FastAPI) -> AsyncIterator[None]:
    """启动时把模型出口打到日志里。

    放在这里而不是装配处：装配在模块导入期完成，那时 uvicorn 还没配好日志，
    写出去的行谁也看不见。而这一行恰恰是最该被看见的事实——
    「产品跑得好好的，只是推理没进平台的账」是一种没有任何症状的偏差，
    日志是它唯一的症状。用 warning 级别，因为直连就是需要被注意的状态。
    """
    logging.getLogger("czghagent_ai").warning(describe_model_exit())
    yield


app = FastAPI(
    title="TenderAgent 文档解析服务",
    version="1.0.0",
    docs_url="/docs",
    redoc_url=None,
    lifespan=_lifespan,
)
app.add_middleware(TaskIdMiddleware)
app.include_router(internal_router)

logger = logging.getLogger("czghagent_ai.errors")

# 状态码到错误码的兜底映射。
#
# 只覆盖框架自己抛的那几种——业务失败都走 ServiceError 带着自己的码。
# 没命中的状态码落到 REQUEST_REJECTED 而不是编一个更具体的名字：
# 一个猜出来的码比一个诚实的通用码更难排查。
_STATUS_CODES: dict[int, str] = {
    401: "AUTH_INTERNAL_TOKEN_INVALID",
    404: "REQUEST_ROUTE_NOT_FOUND",
    405: "REQUEST_METHOD_NOT_ALLOWED",
    409: "DOCUMENT_RENDER_CONFLICT",
    413: "REQUEST_UPLOAD_TOO_LARGE",
    415: "REQUEST_MEDIA_TYPE_UNSUPPORTED",
    422: "REQUEST_VALIDATION_FAILED",
    502: "AI_PROVIDER_ERROR",
    503: "AI_PROVIDER_UNAVAILABLE",
    504: "AI_MODEL_TIMEOUT",
}


@app.exception_handler(ServiceError)
async def service_error(_: Request, exception: ServiceError) -> JSONResponse:
    return JSONResponse(
        status_code=exception.status,
        content=envelope(
            exception.code, exception.message, exception.retryable, exception.field
        ),
    )


@app.exception_handler(StarletteHTTPException)
async def http_error(_: Request, exception: StarletteHTTPException) -> JSONResponse:
    """把框架抛出的 HTTPException 也塞进同一个封套。

    注册在 <b>Starlette 的基类</b>上而不是 FastAPI 的子类：路由未命中时
    404 由 Starlette 自己抛，抛的就是基类。只注册子类的话，这一面最常被撞到的
    那个响应恰好是唯一漏网的——实测确认过，它会以 ``{"detail": "Not Found"}``
    的形状溜出去，也就是这个服务的第二种信封。
    """
    code = _STATUS_CODES.get(exception.status_code, "REQUEST_REJECTED")
    retryable = exception.status_code in (429, 502, 503, 504)
    message = (
        exception.detail if isinstance(exception.detail, str) else "请求未被接受"
    )
    return JSONResponse(
        status_code=exception.status_code, content=envelope(code, message, retryable)
    )


@app.exception_handler(RequestValidationError)
async def validation_error(
    request: Request, exception: RequestValidationError
) -> JSONResponse:
    logger.warning(
        "request_validation_failed path=%s errors=%s",
        request.url.path,
        exception.errors(),
    )
    first = exception.errors()[0] if exception.errors() else None
    # loc 的首段是来源（body / query），字段名从第二段起；只取第一个错误，
    # 因为这一面的调用方是自家 Java 服务，它按字段修一次就够。
    field = (
        ".".join(str(part) for part in first["loc"][1:])
        if first and len(first.get("loc", ())) > 1
        else None
    )
    return JSONResponse(
        status_code=422,
        content=envelope(
            "REQUEST_VALIDATION_FAILED",
            first["msg"] if first else "请求参数不合法",
            False,
            field,
        ),
    )


@app.exception_handler(Exception)
async def unexpected_error(_: Request, exception: Exception) -> JSONResponse:
    logger.exception("unhandled_request_error", exc_info=exception)
    return JSONResponse(
        status_code=500,
        content=envelope("INTERNAL_ERROR", "系统处理失败，请稍后重试", True),
    )


@app.get("/health")
async def health() -> JSONResponse:
    """存活：零依赖。在这里检查模型配置会让一次上游抖动重启掉整个容器。"""
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
