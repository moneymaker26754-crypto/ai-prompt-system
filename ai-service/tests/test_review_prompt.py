from app.prompts.review import build_review_prompt


def test_build_review_prompt_contains_both_prompts():
    result = build_review_prompt(
        original_prompt="原始提示词",
        optimized_prompt="优化提示词",
    )

    assert "原始提示词" in result
    assert "优化提示词" in result
    assert '"score"' in result
    assert '"risk_level"' in result
    assert '"changed_intent"' in result