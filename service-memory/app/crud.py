from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from . import models, schemas

async def create_memory(db: AsyncSession, memory: schemas.MemoryCreate):
    db_memory = models.Memory(
        agent_id=memory.agent_id,
        content=memory.content
    )
    
    db.add(db_memory)
    await db.commit()
    await db.refresh(db_memory)
    
    return db_memory

async def get_memories_by_agent(db: AsyncSession, agent_id: str):
    query = select(models.Memory).where(
        models.Memory.agent_id == agent_id
    ).order_by(models.Memory.created_at.asc())
    
    result = await db.execute(query)
    
    return result.scalars().all()