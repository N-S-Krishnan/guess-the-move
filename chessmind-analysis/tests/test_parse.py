import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

# ---------------------------------------------------------------------------
# PGN fixtures
# ---------------------------------------------------------------------------

VALID_PGN = """[Event "Test Event"]
[Site "?"]
[Date "2024.01.01"]
[White "Magnus Carlsen"]
[Black "Fabiano Caruana"]
[Result "1-0"]

1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 d6 1-0
"""

MULTI_GAME_PGN = """[Event "Game 1"]
[White "Alice"]
[Black "Bob"]
[Result "1-0"]

1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0

[Event "Game 2"]
[White "Carol"]
[Black "Dave"]
[Result "1/2-1/2"]

1. d4 d5 2. c4 c6 1/2-1/2
"""

PGN_WITH_COMMENTS = """[Event "Annotated Game"]
[White "White"]
[Black "Black"]
[Result "*"]

1. e4 {King's pawn opening} e5 {The symmetric response} 2. Nf3 {Develops the knight} Nc6 {Defends the pawn} *
"""

PGN_WITH_VARIATIONS = """[Event "Variation Game"]
[White "White"]
[Black "Black"]
[Result "*"]

1. e4 (1. d4 {Queen's pawn} d5) e5 2. Nf3 (2. Bc4 {Italian} Bc5) Nc6 *
"""

PGN_WITH_NAG = """[Event "NAG Game"]
[White "White"]
[Black "Black"]
[Result "*"]

1. e4! e5? 2. Nf3!! Nc6?? 3. Bb5 *
"""


# ---------------------------------------------------------------------------
# Happy-path tests
# ---------------------------------------------------------------------------


def test_valid_single_game_returns_200() -> None:
    response = client.post("/parse", json={"pgn": VALID_PGN})
    assert response.status_code == 200


def test_valid_game_headers_extracted() -> None:
    response = client.post("/parse", json={"pgn": VALID_PGN})
    data = response.json()
    assert data["white"] == "Magnus Carlsen"
    assert data["black"] == "Fabiano Caruana"
    assert data["event"] == "Test Event"
    assert data["date"] == "2024.01.01"
    assert data["result"] == "1-0"


def test_valid_game_move_counts() -> None:
    response = client.post("/parse", json={"pgn": VALID_PGN})
    data = response.json()
    # Both sides play all 7 moves: e4,e5,Nf3,Nc6,Bb5,a6,Ba4,Nf6,O-O,Be7,Re1,b5,Bb3,d6 = 14 ply
    assert data["ply_count"] == 14
    assert data["full_move_count"] == 7


def test_valid_game_starting_fen_is_initial_position() -> None:
    response = client.post("/parse", json={"pgn": VALID_PGN})
    data = response.json()
    assert data["starting_fen"] == "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"


def test_valid_game_moves_have_san_uci_and_fen() -> None:
    response = client.post("/parse", json={"pgn": VALID_PGN})
    data = response.json()
    moves = data["moves"]
    assert len(moves) == data["ply_count"]
    first_move = moves[0]
    assert first_move["san"] == "e4"
    assert first_move["uci"] == "e2e4"
    assert "fen_after" in first_move
    # FEN after 1.e4 — en passant square e3, White's move count advances
    assert first_move["fen_after"].startswith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR")


def test_valid_game_fen_sequence_is_consistent() -> None:
    """Each fen_after should differ and represent a real board position."""
    response = client.post("/parse", json={"pgn": VALID_PGN})
    data = response.json()
    fens = [m["fen_after"] for m in data["moves"]]
    # All FENs should be unique (no repeated positions in this game)
    assert len(fens) == len(set(fens))


def test_multigame_pgn_returns_first_game_only() -> None:
    response = client.post("/parse", json={"pgn": MULTI_GAME_PGN})
    assert response.status_code == 200
    data = response.json()
    assert data["white"] == "Alice"
    assert data["black"] == "Bob"
    assert data["event"] == "Game 1"
    # Scholar's mate is 4 ply (4 half-moves per side, but White mates on move 4: 7 ply total)
    assert data["ply_count"] == 7


def test_pgn_with_comments_is_parsed_cleanly() -> None:
    response = client.post("/parse", json={"pgn": PGN_WITH_COMMENTS})
    assert response.status_code == 200
    data = response.json()
    assert data["ply_count"] == 4
    moves = data["moves"]
    assert moves[0]["san"] == "e4"
    assert moves[1]["san"] == "e5"
    assert moves[2]["san"] == "Nf3"
    assert moves[3]["san"] == "Nc6"


def test_pgn_with_variations_uses_mainline_only() -> None:
    response = client.post("/parse", json={"pgn": PGN_WITH_VARIATIONS})
    assert response.status_code == 200
    data = response.json()
    # Mainline: 1.e4 e5 2.Nf3 Nc6 — 4 ply
    assert data["ply_count"] == 4
    moves = data["moves"]
    assert moves[0]["san"] == "e4"
    assert moves[2]["san"] == "Nf3"


def test_pgn_with_nag_annotations_is_parsed_cleanly() -> None:
    response = client.post("/parse", json={"pgn": PGN_WITH_NAG})
    assert response.status_code == 200
    data = response.json()
    assert data["ply_count"] == 5
    # NAG symbols must not bleed into the SAN strings
    for move in data["moves"]:
        assert "!" not in move["san"]
        assert "?" not in move["san"]


# ---------------------------------------------------------------------------
# Error-path tests
# ---------------------------------------------------------------------------


def test_empty_string_returns_400() -> None:
    response = client.post("/parse", json={"pgn": ""})
    assert response.status_code == 400


def test_whitespace_only_returns_400() -> None:
    response = client.post("/parse", json={"pgn": "   \n\t  "})
    assert response.status_code == 400


def test_garbage_text_returns_400() -> None:
    response = client.post("/parse", json={"pgn": "this is not a chess game at all"})
    assert response.status_code == 400


def test_illegal_move_in_pgn_returns_400() -> None:
    # After 1.e4 e5, White tries 2.e5 — pushing the pawn into the square occupied by
    # Black's pawn. python-chess detects this as illegal and populates game.errors.
    bad_pgn = """[Event "Bad Game"]
[White "White"]
[Black "Black"]
[Result "*"]

1. e4 e5 2. e5 *
"""
    response = client.post("/parse", json={"pgn": bad_pgn})
    assert response.status_code == 400


def test_error_response_has_detail_field() -> None:
    response = client.post("/parse", json={"pgn": ""})
    assert response.status_code == 400
    body = response.json()
    assert "detail" in body
    assert isinstance(body["detail"], str)
    assert len(body["detail"]) > 0


def test_missing_pgn_field_returns_422() -> None:
    response = client.post("/parse", json={})
    assert response.status_code == 422
