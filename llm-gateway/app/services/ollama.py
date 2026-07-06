import os
import pybreaker
from ollama import AsyncClient

breaker = pybreaker.CircuitBreaker(fail_max=3, reset_timeout=30)

class OllamaService:
    def __init__(self):
        self.client = AsyncClient(host=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"))
        self.model = os.getenv("LLM_MODEL", "qwen2.5")

    @breaker
    async def chat(self, messages, tools=None):
        formatted_messages = [{"role": m.role, "content": m.content} for m in messages]
        
        kwargs = {
            "model": self.model,
            "messages": formatted_messages
        }
        
        if tools:
            kwargs["tools"] = tools
            
        return await self.client.chat(**kwargs)

ollama_service = OllamaService()