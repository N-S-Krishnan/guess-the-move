from fastapi import FastAPI

app = FastAPI(title="chessmind-analysis")

@app.get("/health")
async def health():
    return {"status": "ok"}