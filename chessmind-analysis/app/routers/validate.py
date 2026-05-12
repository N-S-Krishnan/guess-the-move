from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.chess_engine import validate_move

router = APIRouter(prefix="/validate", tags=["validate"])


class ValidateRequest(BaseModel):
    fen: str
    uci_move: str


class ValidateResponse(BaseModel):
    legal: bool
    san: str | None
    fen_after: str | None


@router.post("", response_model=ValidateResponse, status_code=200)
async def validate(body: ValidateRequest) -> ValidateResponse:
    """
    Check whether a UCI move is legal in the given FEN position.
    Returns 400 if the FEN is invalid.
    An illegal or unparseable uci_move returns legal=false rather than an error.
    """
    try:
        result = validate_move(body.fen, body.uci_move)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return ValidateResponse(**result)
