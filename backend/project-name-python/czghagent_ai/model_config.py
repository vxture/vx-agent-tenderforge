# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-27
from enum import StrEnum


class AiModelRequestDialect(StrEnum):
    DEEPSEEK = "deepseek"
    DASHSCOPE = "dashscope"
    OPENAI = "openai"

    @classmethod
    def parse(cls, value: str) -> "AiModelRequestDialect":
        normalized = value.strip().lower()
        try:
            return cls(normalized)
        except ValueError as exception:
            supported = ", ".join(item.value for item in cls)
            raise ValueError(
                f"AI_MODEL_REQUEST_DIALECT must be one of: {supported}"
            ) from exception
