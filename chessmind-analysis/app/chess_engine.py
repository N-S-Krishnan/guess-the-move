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


def validate_move(fen: str, uci_move: str) -> dict[str, Any]:
    """
    Check whether a UCI move is legal in the given position.

    Returns a dict with:
      - legal: True if the move is legal in the position
      - san: SAN string for the move, or None if illegal or unparseable

    Raises ValueError for an invalid FEN — that is a caller error.
    An invalid or illegal uci_move is reported as legal=False rather than
    raising, because it reflects user input that simply does not match any
    legal move.
    """
    try:
        board = chess.Board(fen)
    except ValueError as exc:
        raise ValueError(f"Invalid FEN: {exc}") from exc

    try:
        move = chess.Move.from_uci(uci_move)
    except ValueError:
        return {"legal": False, "san": None, "fen_after": None}

    if move not in board.legal_moves:
        return {"legal": False, "san": None, "fen_after": None}

    san = board.san(move)
    board.push(move)
    return {"legal": True, "san": san, "fen_after": board.fen()}


def compare_move(fen: str, expected_uci: str, submitted_uci: str) -> dict[str, Any]:
    """
    Compare a submitted UCI move against the expected move in a given position.

    Returns a dict with:
      - correct: whether the submitted move matches the expected move
      - legal: whether the submitted move is legal in the position
      - submitted_san: SAN of the submitted move, or None if illegal/invalid
      - expected_san: SAN of the expected (correct) move

    Raises ValueError for an invalid FEN or an invalid expected_uci —
    both indicate a server-side error.  An invalid or illegal submitted_uci
    is treated as a wrong guess (legal=False) rather than an error.
    """
    try:
        board = chess.Board(fen)
    except ValueError as exc:
        raise ValueError(f"Invalid FEN: {exc}") from exc

    # expected_uci must be a valid, legal move — a violation is a caller bug
    try:
        expected_move = chess.Move.from_uci(expected_uci)
        expected_san = board.san(expected_move)
    except (ValueError, chess.IllegalMoveError) as exc:
        raise ValueError(f"Invalid expected_uci '{expected_uci}': {exc}") from exc

    # FEN after the expected (correct) move — returned regardless of whether the
    # guess was right so the board can show the correct position after a reveal.
    board_after = chess.Board(fen)
    board_after.push(expected_move)
    fen_after = board_after.fen()

    # Parse the submitted UCI — an invalid format is just an illegal guess
    try:
        submitted_move = chess.Move.from_uci(submitted_uci)
    except ValueError:
        return {
            "correct": False,
            "legal": False,
            "submitted_san": None,
            "expected_san": expected_san,
            "fen_after": fen_after,
        }

    if submitted_move not in board.legal_moves:
        return {
            "correct": False,
            "legal": False,
            "submitted_san": None,
            "expected_san": expected_san,
            "fen_after": fen_after,
        }

    submitted_san = board.san(submitted_move)
    return {
        "correct": submitted_move == expected_move,
        "legal": True,
        "submitted_san": submitted_san,
        "expected_san": expected_san,
        "fen_after": fen_after,
    }
