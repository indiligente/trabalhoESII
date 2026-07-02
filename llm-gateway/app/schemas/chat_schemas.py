from pydantic import BaseModel
from typing import List, Optional, Dict, Any

class LlmMessage(BaseModel):
    role: str
    content: str

class ToolCall(BaseModel):
    id: str
    name: str
    arguments: Dict[str, Any]

class LlmChatRequest(BaseModel):
    messages: List[LlmMessage]
    availableTools: List[str] = []

class LlmChatResponse(BaseModel):
    content: Optional[str] = None
    toolCalls: List[ToolCall] = []
    finishReason: Optional[str] = "stop"