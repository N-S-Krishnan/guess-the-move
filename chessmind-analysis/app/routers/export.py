from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.chess_engine import export_pgn

router = APIRouter(prefix="/export", tags=["export"])


class AnnotationNode(BaseModel):
    fen: str
    from_fen: str | None = None
    move_uci: str | None = None
    move_san: str | None = None
    comment: str | None = None
    symbol: str | None = None


class ExportRequest(BaseModel):
    pgn_raw: str
    annotations: list[AnnotationNode]


class ExportResponse(BaseModel):
    pgn: str


@router.post("", response_model=ExportResponse, status_code=200)
async def export(body: ExportRequest) -> ExportResponse:
    """
    Reconstruct an annotated PGN from a raw PGN and a flat annotation list.
    Returns 400 if the PGN cannot be parsed.
    """
    try:
        pgn = export_pgn(
            body.pgn_raw,
            [ann.model_dump() for ann in body.annotations],
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return ExportResponse(pgn=pgn)
