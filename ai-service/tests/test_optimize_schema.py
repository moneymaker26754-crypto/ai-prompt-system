import pytest
from pydantic import ValidationError

from app.schemas.optimize import OptimizeRequest


def test_optimize_request_accept_valida_data():
    request = OptimizeRequest(
        original_prompt="帮我写广告词",
        analysis_result="输出格式不明确",
        target="更加专业",
        output_format="三段式",
    )

    assert request.original_prompt == "帮我写广告词"


def test_optimize_request_rejects_blank_original_prompt():
    with pytest.raises(ValidationError):
        OptimizeRequest(
            original_prompt="   ",
            analysis_result="analysis",
        )


def test_optimize_request_rejects_original_prompt_over_5000():
    with pytest.raises(ValidationError):
        OptimizeRequest(
            original_prompt="a" * 5001,
            analysis_result="analysis",
        )