from enum import StrEnum

from pydantic import BaseModel


class StreamEventType(StrEnum):
    TOKEN = "token"
    DONE = "done"
    ERROR = "error"


class OptimizeStreamEvent(BaseModel):
    type: StreamEventType
    content: str =""
    model: str | None = None
    code: str | None = None