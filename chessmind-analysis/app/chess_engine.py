import io
from typing import Any

import chess
import chess.pgn


def parse_pgn(pgn: str) -> dict[str, Any]:
    """
    Parse a PGN string and return a structured representation of the first game.

    Raises ValueError with a human-readable message if the PGN is malformed
    or no game can be extracted.
    """
    pgn_io = io.StringIO(pgn.strip())
    game = chess.pgn.read_game(pgn_io)

    if game is None:
        raise ValueError("No game found in PGN input")

    if game.errors:
        raise ValueError(f"PGN contains illegal moves or is malformed: {game.errors[0]}")

    board = game.board()
    starting_fen = board.fen()
    moves: list[dict[str, str]] = []

    for move in game.mainline_moves():
        san = board.san(move)
        uci = move.uci()
        board.push(move)
        moves.append({"san": san, "uci": uci, "fen_after": board.fen()})

    if not moves:
        raise ValueError("PGN contains no playable moves")

    headers = game.headers
    white = headers.get("White", "?")
    black = headers.get("Black", "?")
    event = headers.get("Event", "?")
    date = headers.get("Date", "????.??.??")
    result = headers.get("Result", "*")

    ply_count = len(moves)
    full_move_count = (ply_count + 1) // 2

    return {
        "white": white,
        "black": black,
        "event": event,
        "date": date,
        "result": result,
        "ply_count": ply_count,
        "full_move_count": full_move_count,
        "starting_fen": starting_fen,
        "moves": moves,
    }
