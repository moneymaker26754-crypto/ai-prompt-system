from app.prompts.optimize import build_optimize_prompt


def test_build_optimize_prompt_uses_custom_values():
    prompt = build_optimize_prompt(
        original_prompt="原始内容",
        analysis_result="分析内容",
        instruction="不要修改语气",
        target="更清晰",
        output_format="JSON",
    )

    assert "不要修改语气" in prompt
    assert "更清晰" in prompt
    assert "JSON" in prompt
    assert "原始内容" in prompt
    assert "分析内容" in prompt


def test_build_optimize_prompt_uses_defaults():
    prompt = build_optimize_prompt(
        original_prompt="hello",
        analysis_result="analysis",
        instruction=None,
        target=None,
        output_format=None,
    )

    assert "提升提示词的清晰度" in prompt
    assert "未指定，请优先提升" in prompt
    assert "未指定，请采用最适合" in prompt