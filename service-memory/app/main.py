from fastapi import FastAPI, Depends
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker
from typing import List
import json
import logging

from . import crud, schemas
from .database import engine, redis_client

AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)

app = FastAPI(title="Memory Service")
logger = logging.getLogger("service-memory")

async def get_db():
    async with AsyncSessionLocal() as session:
        yield session

@app.get("/")
def read_root():
    return {"message": "Memory Service is running!"}

@app.get("/health")
async def health():
    return {"status": "UP", "service": "service-memory"}

@app.post("/api/memory/{sessionId}", response_model=schemas.MemoryResponse)
async def create_memory(sessionId: str, memory: schemas.MemoryCreate, db: AsyncSession = Depends(get_db)):
    new_memory = await crud.create_memory(db=db, memory=memory, agent_id=sessionId)
    
    cache_key = f"memories:{sessionId}"
    try:
        await redis_client.delete(cache_key)
    except Exception as exc:
        logger.warning("Redis indisponivel ao invalidar cache da sessao %s: %s", sessionId, exc)

    logger.info("Mensagem salva na memoria: sessionId=%s role=%s", sessionId, memory.role)
    
    return new_memory

@app.get("/api/memory/{sessionId}", response_model=List[schemas.MemoryMessage])
async def read_memories(sessionId: str, db: AsyncSession = Depends(get_db)):
    cache_key = f"memories:{sessionId}"
    
    try:
        cached_memories = await redis_client.get(cache_key)
        if cached_memories:
            logger.info("Memoria carregada do Redis: sessionId=%s", sessionId)
            return json.loads(cached_memories)
    except Exception as exc:
        logger.warning("Redis indisponivel ao carregar cache da sessao %s: %s", sessionId, exc)
        
    logger.info("Memoria carregada do PostgreSQL: sessionId=%s", sessionId)
    memories = await crud.get_memories_by_agent(db=db, agent_id=sessionId)
    memory_messages = [
        schemas.MemoryMessage(role=memory.role, content=memory.content).model_dump(mode="json")
        for memory in memories
    ]
    
    if memory_messages:
        try:
            await redis_client.setex(cache_key, 3600, json.dumps(memory_messages))
        except Exception as exc:
            logger.warning("Redis indisponivel ao salvar cache da sessao %s: %s", sessionId, exc)
        
    return memory_messages
