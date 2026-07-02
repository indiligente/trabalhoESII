import os
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import declarative_base
from dotenv import load_dotenv
import redis.asyncio as redis

load_dotenv()

DATABASE_URL = os.environ.get("DATABASE_URL")

if not DATABASE_URL:
    raise ValueError("A variavel de ambiente DATABASE_URL nao esta definida")

Base = declarative_base()
engine = create_async_engine(DATABASE_URL, echo=True)

REDIS_URL = os.environ.get("REDIS_URL")
if not REDIS_URL:
    raise ValueError("A variavel de ambiente REDIS_URL nao esta definida")

redis_client = redis.from_url(REDIS_URL, decode_responses=True)