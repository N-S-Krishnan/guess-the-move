from fastapi import FastAPI

from app.routers import parse

app = FastAPI(title="chessmind-analysis")

app.include_router(parse.router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
