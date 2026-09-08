# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-06
import copy
import hashlib
import json
import re
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from typing import Any, Generic, TypeVar

from pydantic import BaseModel, ValidationError

from czghagent_ai.services.ai_provider import (
    AiProviderDiagnostics,
    AiProviderOutputError,
    AiProviderResult,
    TenderAiProvider,
)

ResponseModel = TypeVar("ResponseModel", bound=BaseModel)
Normalizer = Callable[[dict[str, Any]], None]
SemanticValidator = Callable[[ResponseModel], list[str]]


class AiStructuredOutputError(AiProviderOutputError):
    """A model response that remains invalid after one object-level correction."""

    def __init__(
        self,
        message: str,
        *,
        object_name: str,
        schema_version: str,
        attempts: int,
        validation_errors: list[str],
        finish_reason: str | None,
        response_length: int,
        response_hash: str | None,
        input_tokens: int | None = None,
        output_tokens: int | None = None,
        reasoning_tokens: int | None = None,
        cached_input_tokens: int | None = None,
    ) -> None:
        super().__init__(
            message,
            finish_reason=finish_reason,
            response_length=response_length,
            response_hash=response_hash,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            reasoning_tokens=reasoning_tokens,
            cached_input_tokens=cached_input_tokens,
            attempts=attempts,
        )
        self.object_name = object_name
        self.schema_version = schema_version
        self.attempts = attempts
        self.validation_errors = validation_errors
        self.finish_reason = finish_reason
        self.response_length = response_length
        self.response_hash = response_hash

    def details(self) -> dict[str, object]:
        detail = super().details()
        detail.update({
            "objectName": self.object_name,
            "schemaVersion": self.schema_version,
            "attempts": self.attempts,
            "finishReason": self.finish_reason,
            "responseLength": self.response_length,
            "responseHash": self.response_hash,
            "validationErrors": self.validation_errors,
        })
        return detail


@dataclass(frozen=True)
class AiStructuredResult(Generic[ResponseModel]):
    data: ResponseModel
    diagnostics: AiProviderDiagnostics
    attempts: int


class AiStructuredExecutor:
    def __init__(self, provider: TenderAiProvider) -> None:
        self._provider = provider

    async def execute(
        self,
        operation: str,
        payload: dict[str, Any],
        user: str,
        response_model: type[ResponseModel],
        *,
        object_name: str,
        schema_version: str,
        normalizer: Normalizer | None = None,
        semantic_validator: SemanticValidator[ResponseModel] | None = None,
    ) -> ResponseModel:
        result = await self.execute_result(
            operation,
            payload,
            user,
            response_model,
            object_name=object_name,
            schema_version=schema_version,
            normalizer=normalizer,
            semantic_validator=semantic_validator,
        )
        return result.data

    async def execute_result(
        self,
        operation: str,
        payload: dict[str, Any],
        user: str,
        response_model: type[ResponseModel],
        *,
        object_name: str,
        schema_version: str,
        normalizer: Normalizer | None = None,
        semantic_validator: SemanticValidator[ResponseModel] | None = None,
    ) -> AiStructuredResult[ResponseModel]:
        """
        Validate one model-owned object and issue one focused correction when needed.

        Preconditions:
            - response_model describes one bounded business object.
            - payload contains no model credentials.
        Side Effects:
            - Calls the configured model once, or twice after an invalid first response.
        Error Semantics:
            - AiStructuredOutputError contains safe diagnostics after correction fails.
            - Other provider errors propagate unchanged.
        """
        original_payload = payload
        current_payload = payload
        last_errors: list[str] = []
        last_raw = ""
        finish_reason: str | None = None
        response_length = 0
        response_hash: str | None = None
        diagnostics_by_attempt: list[AiProviderDiagnostics] = []
        for attempt in range(1, 3):
            try:
                provider_result = await self._provider.run(
                    operation, current_payload, f"{user}-attempt-{attempt}", response_model
                )
                raw, diagnostics = _unpack(provider_result)
                diagnostics_by_attempt.append(diagnostics)
                finish_reason = diagnostics.finish_reason
                response_length = diagnostics.response_length
                response_hash = diagnostics.response_hash
                normalized = normalize_model_object(raw, response_model)
                if normalizer is not None:
                    normalizer(normalized)
                validated = response_model.model_validate(normalized)
                semantic_errors = (
                    semantic_validator(validated) if semantic_validator is not None else []
                )
                if semantic_errors:
                    last_errors = semantic_errors[:20]
                    last_raw = json.dumps(raw, ensure_ascii=False, default=str)
                    if attempt == 1:
                        current_payload = correction_payload(
                            original_payload, last_raw, last_errors, object_name
                        )
                        continue
                    break
                diagnostics = _merge_diagnostics(diagnostics_by_attempt)
                return AiStructuredResult(validated, diagnostics, diagnostics.attempts)
            except ValidationError as exception:
                last_errors = validation_error_messages(exception)
                last_raw = json.dumps(raw, ensure_ascii=False, default=str)
            except AiProviderOutputError as exception:
                diagnostics_by_attempt.append(exception.diagnostics())
                last_errors = [str(exception)]
                last_raw = exception.raw_output
                finish_reason = exception.finish_reason
                response_length = exception.response_length
                response_hash = exception.response_hash
            if attempt == 1:
                current_payload = correction_payload(
                    original_payload, last_raw, last_errors, object_name
                )
                continue
            raise _structured_error(
                f"{object_name}未通过结构校验",
                object_name=object_name,
                schema_version=schema_version,
                attempts=attempt,
                validation_errors=last_errors,
                finish_reason=finish_reason,
                response_length=response_length,
                response_hash=response_hash,
                diagnostics_by_attempt=diagnostics_by_attempt,
            )
        raise _structured_error(
            f"{object_name}未通过业务完整性校验",
            object_name=object_name,
            schema_version=schema_version,
            attempts=2,
            validation_errors=last_errors,
            finish_reason=finish_reason,
            response_length=response_length,
            response_hash=response_hash,
            diagnostics_by_attempt=diagnostics_by_attempt,
        )


def _unpack(
    result: AiProviderResult | dict[str, Any],
) -> tuple[dict[str, Any], AiProviderDiagnostics]:
    if isinstance(result, AiProviderResult):
        return result.data, result.diagnostics
    encoded = json.dumps(result, ensure_ascii=False, default=str)
    return result, AiProviderDiagnostics(
        finish_reason=None,
        response_length=len(encoded),
        response_hash=hashlib.sha256(encoded.encode("utf-8")).hexdigest(),
        input_tokens=None,
        output_tokens=None,
    )


def _merge_diagnostics(
    values: list[AiProviderDiagnostics],
) -> AiProviderDiagnostics:
    latest = values[-1]
    return AiProviderDiagnostics(
        finish_reason=latest.finish_reason,
        response_length=sum(item.response_length for item in values),
        response_hash=hashlib.sha256(
            ":".join(item.response_hash for item in values).encode("ascii")
        ).hexdigest(),
        input_tokens=_sum_optional(item.input_tokens for item in values),
        output_tokens=_sum_optional(item.output_tokens for item in values),
        reasoning_tokens=_sum_optional(item.reasoning_tokens for item in values),
        cached_input_tokens=_sum_optional(item.cached_input_tokens for item in values),
        attempts=sum(item.attempts for item in values),
    )


def _structured_error(
    message: str,
    *,
    object_name: str,
    schema_version: str,
    attempts: int,
    validation_errors: list[str],
    finish_reason: str | None,
    response_length: int,
    response_hash: str | None,
    diagnostics_by_attempt: list[AiProviderDiagnostics],
) -> AiStructuredOutputError:
    diagnostics = (
        _merge_diagnostics(diagnostics_by_attempt)
        if diagnostics_by_attempt
        else AiProviderDiagnostics(
            finish_reason=finish_reason,
            response_length=response_length,
            response_hash=response_hash or hashlib.sha256(b"").hexdigest(),
            input_tokens=None,
            output_tokens=None,
            attempts=attempts,
        )
    )
    return AiStructuredOutputError(
        message,
        object_name=object_name,
        schema_version=schema_version,
        attempts=diagnostics.attempts,
        validation_errors=validation_errors,
        finish_reason=diagnostics.finish_reason,
        response_length=diagnostics.response_length,
        response_hash=diagnostics.response_hash,
        input_tokens=diagnostics.input_tokens,
        output_tokens=diagnostics.output_tokens,
        reasoning_tokens=diagnostics.reasoning_tokens,
        cached_input_tokens=diagnostics.cached_input_tokens,
    )


def _sum_optional(values: Iterable[int | None]) -> int | None:
    materialized = list(values)
    if all(value is None for value in materialized):
        return None
    return sum(value or 0 for value in materialized)


def normalize_model_object(
    value: dict[str, Any], response_model: type[BaseModel]
) -> dict[str, Any]:
    normalized = copy.deepcopy(value)
    expected = _expected_field_keys(response_model)
    for wrapper in ("data", "result", "output", "response"):
        wrapped = normalized.get(wrapper)
        if not expected.intersection(normalized) and isinstance(wrapped, dict):
            normalized = wrapped
            break
    aliases = {
        _normalized_key(field.alias or name): field.alias or name
        for name, field in response_model.model_fields.items()
    }
    aliases.update({_normalized_key(name): field.alias or name
                    for name, field in response_model.model_fields.items()})
    known = {
        "overview": "projectOverview",
        "projectcontent": "projectOverview",
        "projectsummary": "projectOverview",
        "technicalscoring": "technicalScoringRequirements",
        "scoringrequirements": "technicalScoringRequirements",
        "technicalrequirements": "technicalScoringRequirements",
        "replacementhtml": "content",
        "html": "content",
    }
    remapped: dict[str, Any] = {}
    for key, item in normalized.items():
        normalized_key = _normalized_key(str(key))
        target = aliases.get(normalized_key, known.get(normalized_key, str(key)))
        remapped[target] = item
    if len(response_model.model_fields) == 1 and len(remapped) == 1:
        only_name, only_field = next(iter(response_model.model_fields.items()))
        only_alias = only_field.alias or only_name
        only_value = next(iter(remapped.values()))
        if isinstance(only_value, str):
            return {only_alias: only_value}
    return remapped


def correction_payload(
    original_payload: dict[str, Any],
    invalid_output: str,
    errors: list[str],
    object_name: str,
) -> dict[str, Any]:
    truncated = any("truncated" in error.lower() for error in errors)
    # Keep a bounded prefix so the correction model can recover useful structure
    # without replaying an unbounded or sensitive response.
    invalid_output_limit = 8_000 if truncated else 24_000
    instruction = (
        f"只纠正并返回{object_name}对象。不得解释、不得添加代码围栏，"
        "必须严格满足本次 outputJsonSchema。"
    )
    if truncated:
        instruction += (
            "上次输出因过长而截断；必须压缩表述，遵守 outputJsonSchema 的 maxLength，"
            "并优先保留项目名称、范围、技术内容、参数、期限、交付、验收和服务要求。"
        )
    return {
        "originalInput": original_payload,
        "invalidOutput": invalid_output[:invalid_output_limit],
        "validationErrors": errors[:20],
        "correctionInstruction": instruction,
    }


def validation_error_messages(exception: ValidationError) -> list[str]:
    messages: list[str] = []
    for item in exception.errors(include_url=False, include_input=False):
        location = ".".join(str(part) for part in item.get("loc", ())) or "$"
        messages.append(f"{location}: {item.get('msg', 'invalid value')}")
    return messages[:20]


def _expected_field_keys(response_model: type[BaseModel]) -> set[str]:
    result: set[str] = set()
    for name, field in response_model.model_fields.items():
        result.add(name)
        result.add(field.alias or name)
    return result


def _normalized_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.lower())
