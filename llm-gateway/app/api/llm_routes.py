from fastapi import APIRouter, HTTPException
from app.schemas.chat_schemas import LlmChatRequest, LlmChatResponse
from app.services.ollama import ollama_service 
import pybreaker

router = APIRouter()

@router.post("/chat", response_model=LlmChatResponse)
async def chat(request: LlmChatRequest):
    try:
        response = await ollama_service.chat(request.messages)

        return LlmChatResponse(
            content=response.message.content,
        finishReason="stop"
        )
        
    except pybreaker.CircuitBreakerError:
        raise HTTPException(status_code=503, detail="Ollama está indisponível (Circuit Breaker aberto)")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))