FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8767

ENV PORT=8767 \
    OLLAMA_BASE_URL=http://ollama:11434 \
    LLM_MODEL=qwen3.5 \
    EUREKA_SERVER=http://naming-server:8761/eureka \
    INSTANCE_HOST=llm-gateway

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8767"]
