# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-02
import asyncio
import base64
import logging
import secrets
import time
from collections.abc import Awaitable, Callable
from typing import Annotated, TypeVar

from fastapi import APIRouter, Depends, File, Header, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel

from czghagent_ai.config import settings
from czghagent_ai.document_models import DocumentRenderRequest
from czghagent_ai.errors import ServiceError
from czghagent_ai.models import ParsedDocument
from czghagent_ai.services.ai_provider import (
    AiProviderAuthenticationError,
    AiProviderError,
    AiProviderNotConfiguredError,
    AiProviderTimeoutError,
    OpenAiCompatibleProvider,
)
from czghagent_ai.services.document_parser import DocumentParserService
from czghagent_ai.services.document_qa import DocumentQaError
from czghagent_ai.services.document_renderer import DocumentRenderer
from czghagent_ai.services.file_readers import UnsupportedDocumentError
from czghagent_ai.services.structured_output import AiStructuredResult
from czghagent_ai.services.tender_ai import TenderAiService
from czghagent_ai.strategy_models import BidStrategyResponse
from czghagent_ai.tender_models import (
    AiResponseDiagnostics,
    AiResponseEnvelope,
    BranchBlueprint,
    BranchBlueprintRequest,
    ChapterDraftRequest,
    ChapterDraftResponse,
    InterpretationRequest,
    InterpretationResponse,
    OutlineAssemblyRequest,
    OutlineExpansionResponse,
    OutlineExpansionStageRequest,
    OutlineRequest,
    OutlineResponse,
    OutlineSkeletonPlan,
    OutlineSkeletonStageRequest,
    ProjectOverviewResponse,
    ReviewRequest,
    ReviewResponse,
    RevisionRequest,
    RevisionResponse,
    TechnicalScoringResponse,
)

router = APIRouter(prefix="/internal", tags=["internal"])
logger = logging.getLogger(__name__)
parser_service = DocumentParserService()
document_renderer = DocumentRenderer()
AiResponseData = TypeVar("AiResponseData", bound=BaseModel)


def create_ai_provider() -> OpenAiCompatibleProvider:
    return OpenAiCompatibleProvider(
        settings.ai_model_api_key,
        settings.ai_model_base_url,
        settings.ai_model_name,
        settings.ai_model_timeout_seconds,
        settings.ai_model_max_retries,
        thinking_disabled_operations=settings.ai_model_thinking_disabled_operations,
        quality_model=settings.ai_model_quality_name,
        thinking_enabled_operations=settings.ai_model_thinking_enabled_operations,
        request_dialect=settings.ai_model_request_dialect,
        thinking_budget_tokens=settings.ai_model_thinking_budget_tokens,
    )


tender_ai_service = TenderAiService(create_ai_provider())


def require_internal_token(x_internal_token: str = Header(default="")) -> None:
    """内部服务令牌校验。

    常量时间比较，且不区分「没带」与「带错」——两者返回同一个码，
    因为把它们分开只对攻击者有用。
    """
    if not secrets.compare_digest(x_internal_token, settings.internal_token):
        raise ServiceError(
            "AUTH_INTERNAL_TOKEN_INVALID", "内部服务令牌无效", 401, retryable=False
        )


@router.post("/parse", response_model=ParsedDocument, dependencies=[Depends(require_internal_token)])
async def parse_document(file: Annotated[UploadFile, File()]) -> ParsedDocument:
    content = await file.read()
    try:
        return await asyncio.to_thread(
            parser_service.parse, file.filename or "document", content
        )
    except UnsupportedDocumentError as exception:
        raise ServiceError(
            "PARSER_DOCUMENT_UNSUPPORTED", str(exception), 422, retryable=False
        ) from exception


def map_ai_error(exception: AiProviderError) -> ServiceError:
    """把模型侧失败翻成平台封套。

    ``exception.code`` 原样带出，不在这里重命名——被调方自己的码是调用方
    分支的依据，中途改名等于让上游那条分支永远不进。
    ``retryable`` 由错误码派生（见 :mod:`czghagent_ai.errors`）：
    配置缺失和结构化输出不合法都不会因为等一会儿而变好。
    """
    if isinstance(exception, AiProviderNotConfiguredError | AiProviderAuthenticationError):
        status = 503
    elif isinstance(exception, AiProviderTimeoutError):
        status = 504
    else:
        status = 502
    return ServiceError(
        exception.code, str(exception), status, details=exception.details()
    )


AiStageResult = TypeVar("AiStageResult")


async def run_ai_stage(
    stage: str, action: Callable[[], Awaitable[AiStageResult]]
) -> AiStageResult:
    """
    Execute one bounded AI stage and attach safe timing diagnostics.

    Preconditions:
        - action performs one independently retryable model-owned stage.
    Side Effects:
        - Calls the configured provider and emits metadata-only stage logs.
    Error Semantics:
        - AiProviderTimeoutError: the total Python stage deadline elapsed.
        - AiProviderError: provider, authentication, or structured output failure.
    """
    started = time.monotonic()
    logger.info("ai_stage_started", extra={"ai_stage": stage})
    try:
        async with asyncio.timeout(settings.ai_model_stage_timeout_seconds):
            result = await action()
    except TimeoutError as exception:
        elapsed = round((time.monotonic() - started) * 1000)
        logger.warning(
            "ai_stage_timed_out",
            extra={"ai_stage": stage, "elapsed_millis": elapsed},
        )
        raise AiProviderTimeoutError(
            "AI model stage timed out",
            stage=stage,
            elapsed_millis=elapsed,
        ) from exception
    except AiProviderError as exception:
        elapsed = round((time.monotonic() - started) * 1000)
        logger.warning(
            "ai_stage_failed",
            extra={
                "ai_stage": stage,
                "elapsed_millis": elapsed,
                "error_code": exception.code,
            },
        )
        raise exception.with_context(stage, elapsed) from exception
    elapsed = round((time.monotonic() - started) * 1000)
    logger.info(
        "ai_stage_completed",
        extra={"ai_stage": stage, "elapsed_millis": elapsed},
    )
    return result


def ai_envelope(
    result: AiStructuredResult[AiResponseData],
) -> AiResponseEnvelope[AiResponseData]:
    diagnostics = result.diagnostics
    return AiResponseEnvelope(
        data=result.data,
        diagnostics=AiResponseDiagnostics(
            finish_reason=diagnostics.finish_reason,
            response_length=diagnostics.response_length,
            response_hash=diagnostics.response_hash,
            input_tokens=diagnostics.input_tokens,
            output_tokens=diagnostics.output_tokens,
            reasoning_tokens=diagnostics.reasoning_tokens,
            cached_input_tokens=diagnostics.cached_input_tokens,
            attempts=result.attempts,
        ),
    )


@router.post(
    "/tender/interpretation/project-overview",
    response_model=AiResponseEnvelope[ProjectOverviewResponse],
    dependencies=[Depends(require_internal_token)],
)
async def extract_project_overview(
    body: InterpretationRequest,
) -> AiResponseEnvelope[ProjectOverviewResponse]:
    try:
        result = await run_ai_stage(
            "project_overview",
            lambda: tender_ai_service.extract_project_overview_result(body),
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/interpretation/technical-scoring",
    response_model=AiResponseEnvelope[TechnicalScoringResponse],
    dependencies=[Depends(require_internal_token)],
)
async def extract_technical_scoring(
    body: InterpretationRequest,
) -> AiResponseEnvelope[TechnicalScoringResponse]:
    try:
        result = await run_ai_stage(
            "technical_scoring",
            lambda: tender_ai_service.extract_technical_scoring_result(body),
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/interpretation",
    response_model=InterpretationResponse,
    dependencies=[Depends(require_internal_token)],
)
async def interpret_tender(body: InterpretationRequest) -> InterpretationResponse:
    try:
        return await run_ai_stage("interpretation", lambda: tender_ai_service.interpret(body))
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/outline",
    response_model=AiResponseEnvelope[OutlineResponse],
    dependencies=[Depends(require_internal_token)],
)
async def plan_outline(body: OutlineRequest) -> AiResponseEnvelope[OutlineResponse]:
    try:
        result = await run_ai_stage(
            "outline_legacy", lambda: tender_ai_service.outline_result(body)
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/outline/strategy",
    response_model=AiResponseEnvelope[BidStrategyResponse],
    dependencies=[Depends(require_internal_token)],
)
async def plan_outline_strategy(
    body: OutlineRequest,
) -> AiResponseEnvelope[BidStrategyResponse]:
    try:
        result = await run_ai_stage(
            "outline_strategy",
            lambda: tender_ai_service.outline_strategy_result(body),
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/outline/skeleton",
    response_model=AiResponseEnvelope[OutlineSkeletonPlan],
    dependencies=[Depends(require_internal_token)],
)
async def plan_outline_skeleton(
    body: OutlineSkeletonStageRequest,
) -> AiResponseEnvelope[OutlineSkeletonPlan]:
    try:
        result = await run_ai_stage(
            "outline_skeleton",
            lambda: tender_ai_service.outline_skeleton_result(body),
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/outline/expansion",
    response_model=AiResponseEnvelope[OutlineExpansionResponse],
    dependencies=[Depends(require_internal_token)],
)
async def expand_outline_branch(
    body: OutlineExpansionStageRequest,
) -> AiResponseEnvelope[OutlineExpansionResponse]:
    try:
        result = await run_ai_stage(
            f"outline_expansion_{body.batch_index}",
            lambda: tender_ai_service.outline_expansion_result(body),
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/outline/assemble",
    response_model=OutlineResponse,
    dependencies=[Depends(require_internal_token)],
)
async def assemble_outline(body: OutlineAssemblyRequest) -> OutlineResponse:
    try:
        return tender_ai_service.assemble_outline(body)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/chapter/blueprint",
    response_model=AiResponseEnvelope[BranchBlueprint],
    dependencies=[Depends(require_internal_token)],
)
async def plan_branch_blueprint(
    body: BranchBlueprintRequest,
) -> AiResponseEnvelope[BranchBlueprint]:
    try:
        result = await run_ai_stage(
            "branch_blueprint",
            lambda: tender_ai_service.plan_branch_blueprint_result(body),
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/chapter",
    response_model=AiResponseEnvelope[ChapterDraftResponse],
    dependencies=[Depends(require_internal_token)],
)
async def draft_chapter(body: ChapterDraftRequest) -> AiResponseEnvelope[ChapterDraftResponse]:
    try:
        result = await run_ai_stage(
            "chapter", lambda: tender_ai_service.draft_result(body)
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/revision",
    response_model=AiResponseEnvelope[RevisionResponse],
    dependencies=[Depends(require_internal_token)],
)
async def revise_section(body: RevisionRequest) -> AiResponseEnvelope[RevisionResponse]:
    try:
        result = await run_ai_stage(
            "revision", lambda: tender_ai_service.revise_result(body)
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/review",
    response_model=AiResponseEnvelope[ReviewResponse],
    dependencies=[Depends(require_internal_token)],
)
async def review_bid(body: ReviewRequest) -> AiResponseEnvelope[ReviewResponse]:
    try:
        result = await run_ai_stage(
            "review", lambda: tender_ai_service.review_result(body)
        )
        return ai_envelope(result)
    except AiProviderError as exception:
        raise map_ai_error(exception) from exception


@router.post(
    "/tender/document/render",
    dependencies=[Depends(require_internal_token)],
    response_class=Response,
)
async def render_tender_document(body: DocumentRenderRequest) -> Response:
    try:
        content, qa = await asyncio.to_thread(document_renderer.render, body)
    except ValueError as exception:
        raise ServiceError(
            "DOCUMENT_RENDER_CONFLICT", str(exception), 409, retryable=False
        ) from exception
    except DocumentQaError as exception:
        raise ServiceError(
            "DOCUMENT_RENDER_FAILED", str(exception), 502, retryable=True
        ) from exception
    encoded_summary = base64.urlsafe_b64encode(qa.summary.encode("utf-8")).decode("ascii")
    return Response(
        content=content,
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        headers={
            "Content-Disposition": 'attachment; filename="tender.docx"',
            "X-Actual-Pages": "" if qa.actual_pages is None else str(qa.actual_pages),
            "X-QA-Status": qa.status,
            "X-QA-Summary-Base64": encoded_summary,
        },
    )
