from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    OLLAMA_BASE_URL: str = "http://ollama:11434"
    LLM_MODEL: str = "qwen3.5"
    EUREKA_SERVER: str = "http://naming-server:8761/eureka"
    INSTANCE_HOST: str = "llm-gateway"
    PORT: int = 8767

    class Config:
        env_file = ".env"

settings = Settings()