from fastapi import FastAPI

app = FastAPI()

@app.get("/")
def read_root():
    return {"message": "Memory Service is running!"}

@app.get("/health")
def health_check():
    return {"status": "ok"}
