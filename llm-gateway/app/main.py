from fastapi import FastAPI
from app.api.llm_routes import router as llm_router

app = FastAPI(title="LLM Gateway", version="1.0.0")

app.include_router(llm_router, prefix="/api/llm")

@app.get("/health")
def health():
    return {"status": "UP", "service": "llm-gateway"}