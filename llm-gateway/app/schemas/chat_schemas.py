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
    # Atualizado para receber os schemas das ferramentas (formato JSON Schema esperado pelo LLM)
    availableTools: Optional[List[Dict[str, Any]]] = None 

class LlmChatResponse(BaseModel):
    content: Optional[str] = None
    toolCalls: List[ToolCall] = []
    finishReason: Optional[str] = "stop"