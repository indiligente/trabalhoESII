from fastapi import FastAPI, Depends
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker
from typing import List
import json

from . import crud, schemas
from .database import engine, redis_client

AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)

app = FastAPI(title="Memory Service")

async def get_db():
    async with AsyncSessionLocal() as session:
        yield session

@app.get("/")
def read_root():
    return {"message": "Memory Service is running!"}

@app.post("/memories/", response_model=schemas.MemoryResponse)
async def create_memory(memory: schemas.MemoryCreate, db: AsyncSession = Depends(get_db)):
    new_memory = await crud.create_memory(db=db, memory=memory)
    
    cache_key = f"memories:{memory.agent_id}"
    await redis_client.delete(cache_key)
    
    return new_memory

@app.get("/memories/{agent_id}", response_model=List[schemas.MemoryResponse])
async def read_memories(agent_id: str, db: AsyncSession = Depends(get_db)):
    cache_key = f"memories:{agent_id}"
    
    # 1. Tenta buscar no Redis primeiro
    cached_memories = await redis_client.get(cache_key)
    if cached_memories:
        print("Buscando do Cache (Redis)!")
        return json.loads(cached_memories)
        
    print("Buscando do Banco de Dados (PostgreSQL)!")
    # 2. Se não tem no cache, busca no PostgreSQL
    memories = await crud.get_memories_by_agent(db=db, agent_id=agent_id)
    
    # 3. Transforma o resultado em um formato que o Redis aceita (JSON) e salva por 1 hora (3600 segundos)
    if memories:
        memories_dict = [schemas.MemoryResponse.model_validate(m).model_dump(mode='json') for m in memories]
        await redis_client.setex(cache_key, 3600, json.dumps(memories_dict))
        
    return memories