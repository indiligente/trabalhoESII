from pydantic import BaseModel, ConfigDict
from datetime import datetime

class MemoryBase(BaseModel):
    agent_id: str
    content: str

class MemoryCreate(MemoryBase):
    pass

class MemoryResponse(MemoryBase):
    id: int
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)