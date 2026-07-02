import logging
import os
from contextlib import asynccontextmanager
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from ollama import AsyncClient
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
LLM_MODEL       = os.getenv("LLM_MODEL", "qwen3.5")
EUREKA_SERVER   = os.getenv("EUREKA_SERVER", "http://localhost:8761/eureka")
INSTANCE_HOST   = os.getenv("INSTANCE_HOST", "localhost")
INSTANCE_PORT   = int(os.getenv("PORT", "8767"))

ollama_client = AsyncClient(host=OLLAMA_BASE_URL)


# ── Eureka registration (graceful degradation if lib not present) ────────────
async def _register_eureka():
    try:
        import py_eureka_client.eureka_client as eureka_client
        await eureka_client.init_async(
            eureka_server=EUREKA_SERVER,
            app_name="llm-gateway",
            instance_port=INSTANCE_PORT,
            instance_host=INSTANCE_HOST,
        )
        logger.info("Registered with Eureka at %s", EUREKA_SERVER)
    except Exception as exc:
        logger.warning("Eureka registration skipped: %s", exc)


async def _deregister_eureka():
    try:
        import py_eureka_client.eureka_client as eureka_client
        await eureka_client.stop_async()
    except Exception:
        pass


@asynccontextmanager
async def lifespan(app: FastAPI):
    await _register_eureka()
    yield
    await _deregister_eureka()


app = FastAPI(title="LLM Gateway", version="1.0.0", lifespan=lifespan)


# ── DTOs ─────────────────────────────────────────────────────────────────────
class LlmMessage(BaseModel):
    role: str
    content: str


class LlmChatRequest(BaseModel):
    messages: List[LlmMessage]
    availableTools: List[str] = []


class ToolCall(BaseModel):
    id: str
    name: str
    arguments: str


class LlmChatResponse(BaseModel):
    content: Optional[str] = None
    toolCalls: List[ToolCall] = []
    finishReason: Optional[str] = None


# ── Endpoints ─────────────────────────────────────────────────────────────────
@app.post("/api/llm/chat", response_model=LlmChatResponse)
async def chat(request: LlmChatRequest):
    messages = [{"role": m.role, "content": m.content} for m in request.messages]
    logger.info("chat — %d messages, tools=%s", len(messages), request.availableTools)

    try:
        response = await ollama_client.chat(
            model=LLM_MODEL,
            messages=messages,
        )
    except Exception as exc:
        logger.error("Ollama call failed: %s", exc)
        raise HTTPException(status_code=503, detail=f"LLM unavailable: {exc}")

    content = response.message.content or ""
    finish_reason = "stop"

    tool_calls: List[ToolCall] = []
    raw_tool_calls = getattr(response.message, "tool_calls", None)
    if raw_tool_calls:
        for tc in raw_tool_calls:
            tool_calls.append(ToolCall(
                id="",
                name=tc.function.name,
                arguments=str(tc.function.arguments or "{}"),
            ))

    logger.info("Response: content_len=%d, tool_calls=%d", len(content), len(tool_calls))
    return LlmChatResponse(content=content, toolCalls=tool_calls, finishReason=finish_reason)


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "llm-gateway",
        "model": LLM_MODEL,
        "ollamaBase": OLLAMA_BASE_URL,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=INSTANCE_PORT)
