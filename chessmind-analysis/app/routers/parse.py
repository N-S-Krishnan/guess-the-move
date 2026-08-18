from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.chess_engine import parse_pgn

router = APIRouter(prefix="/parse", tags=["parse"])


class ParseRequest(BaseModel):
    pgn: str


class MoveEntry(BaseModel):
    san: str
    uci: str
    fen_after: str


class ParseResponse(BaseModel):
    white: str
    black: str
    event: str
    date: str
    result: str
    ply_count: int
    full_move_count: int
    starting_fen: str
    moves: list[MoveEntry]


@router.post("", response_model=ParseResponse, status_code=200)
async def parse(body: ParseRequest) -> ParseResponse:
    """
    Parse a raw PGN string and return the structured game representation.
    If the PGN is malformed or empty, returns 400 with a human-readable error.
    Multi-game PGN strings are accepted; only the first game is processed.
    """
    try:
        result = parse_pgn(body.pgn)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return ParseResponse(**result)
