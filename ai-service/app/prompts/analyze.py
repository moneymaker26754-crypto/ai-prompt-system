ANALYZE_PROMPT_TEMPLATE = """\
请分析以下提示词，并从表达清晰度、约束条件、输出格式、上下文完整性四个方面指出问题。

原始提示词：
{original_prompt}

请直接输出分析结果，语言简洁明确。
"""


def build_analyze_prompt(original_prompt: str) -> str:
    return ANALYZE_PROMPT_TEMPLATE.format(
        original_prompt=original_prompt
    )