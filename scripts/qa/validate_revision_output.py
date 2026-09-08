# GENERATED_BY_AI
# MODEL: gpt-5
# DATE: 2026-08-02
import sys

from pydantic import ValidationError

from czghagent_ai.services.tender_ai import normalize_model_blocks
from czghagent_ai.tender_models import RevisionResponse


content = sys.stdin.read().strip()
print(f"inputChars={len(content)}")
try:
    RevisionResponse.model_validate_json(content)
    print("validation=PASS")
except ValidationError:
    raw = __import__("json").loads(content)
    normalize_model_blocks(raw)
    try:
        RevisionResponse.model_validate(raw)
        print("validation=PASS_AFTER_NORMALIZATION")
    except ValidationError as normalized_exception:
        print("validation=FAIL")
        print(normalized_exception.errors(include_input=False))
