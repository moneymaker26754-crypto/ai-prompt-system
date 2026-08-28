REVIEW_PROMPT_TEMPLATE = """\
请审核优化后的提示词是否保持原始意图。

只返回 JSON，不要返回 Markdown、解释或其他文本。

必须严格按照以下结构返回：

{{
  "score": 0到100之间的整数,
  "risk_level": "LOW/MEDIUM/HIGH",
  "changed_intent": true或false,
  "review_comment": "审核意见"
}}

评分越高表示优化结果越合理。

风险等级：
LOW：基本保持原意，没有明显风险。
MEDIUM：存在一定语义变化或约束变化。
HIGH：明显改变原始意图，或优化结果存在严重问题。

原始提示词：
{original_prompt}

优化后的提示词：
{optimized_prompt}
"""

# 用*强制参数用关键字参数(类似original_prompt="")，防止参数顺序搞混
def build_review_prompt(
        *,
        original_prompt: str,
        optimized_prompt: str,
) -> str:
    return REVIEW_PROMPT_TEMPLATE.format(
        original_prompt=original_prompt,
        optimized_prompt=optimized_prompt,
    )