OPTIMIZE_PROMPT_TEMPLATE = """\
请基于以下信息优化提示词，并且只返回优化后的提示词内容，不要附加解释、标题或 Markdown。

优化指令：
{instruction}

优化目标：
{target}

期望输出格式：
{output_format}

原始提示词：
{original_prompt}

分析结果：
{analysis_result}
"""


DEFAULT_INSTRUCTION = (
    "请在不改变原始意图的前提下，"
    "提升提示词的清晰度、完整性和可执行性。"
)

DEFAULT_TARGET = (
    "未指定，请优先提升表达清晰度和执行可操作性。"
)

DEFAULT_OUTPUT_FORMAT = (
    "未指定，请采用最适合任务目标的输出形式。"
)


def build_optimize_prompt(
        *,
        original_prompt: str,
        analysis_result: str,
        instruction: str | None,
        target: str | None,
        output_format: str | None,
) -> str:
    return OPTIMIZE_PROMPT_TEMPLATE.format(
        instruction=instruction or DEFAULT_INSTRUCTION,
        target=target or DEFAULT_TARGET,
        output_format=(
                output_format or DEFAULT_OUTPUT_FORMAT
        ),
        original_prompt=original_prompt,
        analysis_result=(
                analysis_result or "无额外分析结果"
        ),
    )