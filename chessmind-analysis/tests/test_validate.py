import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

# ---------------------------------------------------------------------------
# FEN fixtures
# ---------------------------------------------------------------------------

# Standard starting position (white to move)
STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

# Both sides retain full castling rights with clear back ranks
CASTLING_FEN = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"

# White pawn on e7 ready to promote; kings on e1 and a3 (no check after promotion)
PROMOTION_FEN = "8/4P3/8/8/8/k7/8/4K3 w - - 0 1"


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------


def post_validate(fen: str, uci_move: str) -> dict:
    return client.post("/validate", json={"fen": fen, "uci_move": uci_move})


# ---------------------------------------------------------------------------
# Legal regular moves  (ROADMAP: "correct guess" / "incorrect but legal guess")
#
# /validate is position-only — it cannot distinguish a correct guess from an
# incorrect-but-legal one.  Both are represented here as moves that are legal
# in the position and therefore return legal=true with a populated san.
# ---------------------------------------------------------------------------


def test_legal_move_returns_200() -> None:
    assert post_validate(STARTING_FEN, "e2e4").status_code == 200


def test_legal_move_legal_flag_is_true() -> None:
    data = post_validate(STARTING_FEN, "e2e4").json()
    assert data["legal"] is True


def test_legal_move_san_is_populated() -> None:
    data = post_validate(STARTING_FEN, "e2e4").json()
    assert data["san"] == "e4"


def test_legal_move_fen_after_is_populated() -> None:
    data = post_validate(STARTING_FEN, "e2e4").json()
    assert data["fen_after"] is not None
    assert "e4" in data["fen_after"] or "4" in data["fen_after"]


def test_legal_move_fen_after_reflects_new_position() -> None:
    # After e2e4 from the starting position it is black's turn
    data = post_validate(STARTING_FEN, "e2e4").json()
    assert data["fen_after"].split(" ")[1] == "b"


def test_another_legal_move_is_also_reported_legal() -> None:
    # d2d4 is legal from the starting position — analogous to an incorrect-but-legal guess
    data = post_validate(STARTING_FEN, "d2d4").json()
    assert data["legal"] is True
    assert data["san"] == "d4"


def test_knight_move_from_starting_position_is_legal() -> None:
    data = post_validate(STARTING_FEN, "g1f3").json()
    assert data["legal"] is True
    assert data["san"] == "Nf3"


# ---------------------------------------------------------------------------
# Illegal move submitted  (ROADMAP: "illegal move submitted")
# ---------------------------------------------------------------------------


def test_illegal_move_returns_200() -> None:
    # e2e5 is not a legal pawn move (three squares forward)
    assert post_validate(STARTING_FEN, "e2e5").status_code == 200


def test_illegal_move_legal_flag_is_false() -> None:
    data = post_validate(STARTING_FEN, "e2e5").json()
    assert data["legal"] is False


def test_illegal_move_san_is_null() -> None:
    data = post_validate(STARTING_FEN, "e2e5").json()
    assert data["san"] is None


def test_illegal_move_fen_after_is_null() -> None:
    data = post_validate(STARTING_FEN, "e2e5").json()
    assert data["fen_after"] is None


def test_moving_opponents_piece_is_illegal() -> None:
    # e7e5 attempts to move a black pawn when it is white's turn
    data = post_validate(STARTING_FEN, "e7e5").json()
    assert data["legal"] is False
    assert data["san"] is None


def test_malformed_uci_string_is_treated_as_illegal() -> None:
    # "xyz" is not a valid UCI format — must not raise, just return legal=false
    data = post_validate(STARTING_FEN, "xyz").json()
    assert data["legal"] is False
    assert data["san"] is None


def test_empty_uci_string_is_treated_as_illegal() -> None:
    data = post_validate(STARTING_FEN, "").json()
    assert data["legal"] is False
    assert data["san"] is None


# ---------------------------------------------------------------------------
# Promotion moves  (ROADMAP: "promotion moves")
# ---------------------------------------------------------------------------


def test_queen_promotion_returns_200() -> None:
    assert post_validate(PROMOTION_FEN, "e7e8q").status_code == 200


def test_queen_promotion_is_legal() -> None:
    data = post_validate(PROMOTION_FEN, "e7e8q").json()
    assert data["legal"] is True


def test_queen_promotion_san_contains_queen_symbol() -> None:
    data = post_validate(PROMOTION_FEN, "e7e8q").json()
    assert data["san"] == "e8=Q"


def test_knight_promotion_is_legal() -> None:
    data = post_validate(PROMOTION_FEN, "e7e8n").json()
    assert data["legal"] is True
    assert data["san"] == "e8=N"


def test_bishop_promotion_is_legal() -> None:
    data = post_validate(PROMOTION_FEN, "e7e8b").json()
    assert data["legal"] is True
    assert data["san"] == "e8=B"


def test_rook_promotion_is_legal() -> None:
    data = post_validate(PROMOTION_FEN, "e7e8r").json()
    assert data["legal"] is True
    assert data["san"] == "e8=R"


def test_promotion_without_piece_suffix_is_illegal() -> None:
    # "e7e8" parses as a Move object but is not in legal_moves (promotion requires a piece)
    data = post_validate(PROMOTION_FEN, "e7e8").json()
    assert data["legal"] is False
    assert data["san"] is None


# ---------------------------------------------------------------------------
# Castling  (ROADMAP: "castling")
# ---------------------------------------------------------------------------


def test_kingside_castling_returns_200() -> None:
    assert post_validate(CASTLING_FEN, "e1g1").status_code == 200


def test_kingside_castling_is_legal() -> None:
    data = post_validate(CASTLING_FEN, "e1g1").json()
    assert data["legal"] is True


def test_kingside_castling_san_is_o_o() -> None:
    data = post_validate(CASTLING_FEN, "e1g1").json()
    assert data["san"] == "O-O"


def test_queenside_castling_is_legal() -> None:
    data = post_validate(CASTLING_FEN, "e1c1").json()
    assert data["legal"] is True


def test_queenside_castling_san_is_o_o_o() -> None:
    data = post_validate(CASTLING_FEN, "e1c1").json()
    assert data["san"] == "O-O-O"


def test_castling_without_rights_is_illegal() -> None:
    # Strip castling rights from the FEN — e1g1 is no longer a legal move
    no_rights_fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
    data = post_validate(no_rights_fen, "e1g1").json()
    assert data["legal"] is False
    assert data["san"] is None


def test_castling_through_occupied_square_is_illegal() -> None:
    # Knight on f1 blocks kingside castling
    blocked_fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3KN1R w KQkq - 0 1"
    data = post_validate(blocked_fen, "e1g1").json()
    assert data["legal"] is False
    assert data["san"] is None


# ---------------------------------------------------------------------------
# Error cases — invalid FEN
# ---------------------------------------------------------------------------


def test_invalid_fen_returns_400() -> None:
    assert post_validate("not-a-fen", "e2e4").status_code == 400


def test_invalid_fen_response_has_detail_field() -> None:
    r = post_validate("not-a-fen", "e2e4")
    body = r.json()
    assert "detail" in body
    assert isinstance(body["detail"], str)


def test_missing_field_returns_422() -> None:
    r = client.post("/validate", json={"fen": STARTING_FEN})
    assert r.status_code == 422
