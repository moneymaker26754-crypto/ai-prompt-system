from pydantic import BaseModel


class OllamaGenerateResponse(BaseModel):
    model: str
    response: str
    done: bool = True

    total_duration: int | None = None
    prompt_eval_count: int | None = None
    eval_count: int | None = None