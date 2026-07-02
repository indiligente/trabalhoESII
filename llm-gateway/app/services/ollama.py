import os
import pybreaker
from ollama import AsyncClient

# Configura o disjuntor (abre após 3 falhas, bloqueia por 30s)
breaker = pybreaker.CircuitBreaker(fail_max=3, reset_timeout=30)

class OllamaService:
    def __init__(self):
        self.client = AsyncClient(host=os.getenv("OLLAMA_BASE_URL", "http://ollama:11434"))
        self.model = os.getenv("LLM_MODEL", "qwen3.5")

    @breaker
    async def chat(self, messages):
        # Transforma o schema em formato esperado pelo Ollama
        formatted_messages = [{"role": m.role, "content": m.content} for m in messages]
        
        return await self.client.chat(
            model=self.model,
            messages=formatted_messages
        )

# Instância única para reutilizar em toda a app
ollama_service = OllamaService()