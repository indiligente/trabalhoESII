from fastapi import APIRouter, HTTPException
from app.schemas.chat_schemas import LlmChatRequest, LlmChatResponse, ToolCall
from app.services.ollama import ollama_service 
import pybreaker

router = APIRouter()

@router.post("/chat", response_model=LlmChatResponse)
async def chat(request: LlmChatRequest):
    try:
        response = await ollama_service.chat(request.messages, tools=request.availableTools)

        tool_calls = []
        
        if hasattr(response.message, 'tool_calls') and response.message.tool_calls:
            tool_calls = [
                ToolCall(
                    id=getattr(tc, 'id', tc.function.name),
                    name=tc.function.name, 
                    arguments=tc.function.arguments
                )
                for tc in response.message.tool_calls
            ]

        return LlmChatResponse(
            content=response.message.content,
            toolCalls=tool_calls,
            finishReason="tool_calls" if tool_calls else "stop"
        )
        
    except pybreaker.CircuitBreakerError:
        raise HTTPException(status_code=503, detail="Ollama está indisponível (Circuit Breaker aberto)")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))