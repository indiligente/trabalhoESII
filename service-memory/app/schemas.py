from pydantic import BaseModel, ConfigDict
from datetime import datetime

class MemoryBase(BaseModel):
    role: str
    content: str

class MemoryCreate(MemoryBase):
    pass

class MemoryResponse(MemoryBase):
    id: int
    agent_id: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)