from fastapi import APIRouter, HTTPException
from app.schemas.chat_schemas import LlmChatRequest, LlmChatResponse
from app.services.ollama_proxy import ollama_service 

router = APIRouter()

@router.post("/chat", response_model=LlmChatResponse)
async def chat(request: LlmChatRequest):
    try:
        # A lógica de chamada ao Ollama virá aqui através do serviço
        # response = await ollama_service.chat(request.messages)
        # return response
        
        # Exemplo temporário para testar a rota:
        return LlmChatResponse(content="Gateway funcional", finishReason="stop")
        
    except Exception as e:
        raise HTTPException(status_code=503, detail=str(e))