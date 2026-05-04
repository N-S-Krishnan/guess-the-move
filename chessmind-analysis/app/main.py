from fastapi import FastAPI

from app.routers import compare, parse, validate

app = FastAPI(title="chessmind-analysis")

app.include_router(parse.router)
app.include_router(compare.router)
app.include_router(validate.router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
