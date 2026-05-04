from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.chess_engine import compare_move

router = APIRouter(prefix="/compare", tags=["compare"])


class CompareRequest(BaseModel):
    fen: str
    expected_uci: str
    submitted_uci: str


class CompareResponse(BaseModel):
    correct: bool
    legal: bool
    submitted_san: str | None
    expected_san: str
    fen_after: str


@router.post("", response_model=CompareResponse, status_code=200)
async def compare(body: CompareRequest) -> CompareResponse:
    """
    Compare a submitted UCI move against the expected move in a given FEN position.
    Returns whether the move is correct and legal, along with SAN representations.
    Returns 400 if the FEN or expected_uci is invalid.
    """
    try:
        result = compare_move(body.fen, body.expected_uci, body.submitted_uci)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return CompareResponse(**result)
