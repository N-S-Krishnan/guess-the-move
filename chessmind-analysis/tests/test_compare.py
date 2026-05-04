import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

# ---------------------------------------------------------------------------
# FEN fixtures
# ---------------------------------------------------------------------------

# Standard starting position — used for simple correct/incorrect/illegal tests
STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

# Both sides have kingside and queenside castling rights with clear back ranks
CASTLING_FEN = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"

# White pawn on e7 ready to promote; kings on e1 and a3 (no check after e8=Q)
PROMOTION_FEN = "8/4P3/8/8/8/k7/8/4K3 w - - 0 1"


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------


def post_compare(fen: str, expected_uci: str, submitted_uci: str) -> dict:
    response = client.post(
        "/compare",
        json={"fen": fen, "expected_uci": expected_uci, "submitted_uci": submitted_uci},
    )
    return response


# ---------------------------------------------------------------------------
# Correct guess
# ---------------------------------------------------------------------------


def test_correct_guess_returns_200() -> None:
    r = post_compare(STARTING_FEN, "e2e4", "e2e4")
    assert r.status_code == 200


def test_correct_guess_correct_flag_is_true() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e4").json()
    assert data["correct"] is True


def test_correct_guess_legal_flag_is_true() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e4").json()
    assert data["legal"] is True


def test_correct_guess_submitted_san_matches_expected_san() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e4").json()
    assert data["submitted_san"] == "e4"
    assert data["expected_san"] == "e4"
    assert data["submitted_san"] == data["expected_san"]


def test_correct_guess_fen_after_is_position_after_expected_move() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e4").json()
    assert "fen_after" in data
    assert isinstance(data["fen_after"], str)
    # After 1. e4, the white pawn has moved from e2 to e4
    assert "4P3" in data["fen_after"] or "/8/4P" in data["fen_after"] or "e3" in data["fen_after"]


def test_fen_after_is_present_on_incorrect_but_legal_guess() -> None:
    # fen_after reflects the expected move (e4), not the submitted move (d4)
    data = post_compare(STARTING_FEN, "e2e4", "d2d4").json()
    assert "fen_after" in data
    assert isinstance(data["fen_after"], str)


def test_fen_after_is_present_on_illegal_guess() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e5").json()
    assert "fen_after" in data
    assert isinstance(data["fen_after"], str)


def test_fen_after_matches_regardless_of_guess_correctness() -> None:
    correct_data = post_compare(STARTING_FEN, "e2e4", "e2e4").json()
    wrong_data = post_compare(STARTING_FEN, "e2e4", "d2d4").json()
    # fen_after is always the FEN after the expected move, so both should be equal
    assert correct_data["fen_after"] == wrong_data["fen_after"]


# ---------------------------------------------------------------------------
# Incorrect but legal guess
# ---------------------------------------------------------------------------


def test_incorrect_legal_guess_returns_200() -> None:
    r = post_compare(STARTING_FEN, "e2e4", "d2d4")
    assert r.status_code == 200


def test_incorrect_legal_guess_correct_flag_is_false() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "d2d4").json()
    assert data["correct"] is False


def test_incorrect_legal_guess_legal_flag_is_true() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "d2d4").json()
    assert data["legal"] is True


def test_incorrect_legal_guess_sans_differ() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "d2d4").json()
    assert data["submitted_san"] == "d4"
    assert data["expected_san"] == "e4"
    assert data["submitted_san"] != data["expected_san"]


# ---------------------------------------------------------------------------
# Illegal move submitted
# ---------------------------------------------------------------------------


def test_illegal_move_returns_200() -> None:
    # e2e5 is not a legal pawn move (three squares)
    r = post_compare(STARTING_FEN, "e2e4", "e2e5")
    assert r.status_code == 200


def test_illegal_move_correct_flag_is_false() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e5").json()
    assert data["correct"] is False


def test_illegal_move_legal_flag_is_false() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e5").json()
    assert data["legal"] is False


def test_illegal_move_submitted_san_is_null() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e5").json()
    assert data["submitted_san"] is None


def test_illegal_move_expected_san_is_still_returned() -> None:
    data = post_compare(STARTING_FEN, "e2e4", "e2e5").json()
    assert data["expected_san"] == "e4"


def test_malformed_uci_string_treated_as_illegal() -> None:
    # "xyz" is not a valid UCI format at all
    data = post_compare(STARTING_FEN, "e2e4", "xyz").json()
    assert data["correct"] is False
    assert data["legal"] is False
    assert data["submitted_san"] is None


# ---------------------------------------------------------------------------
# Promotion moves
# ---------------------------------------------------------------------------


def test_correct_queen_promotion_returns_correct_true() -> None:
    data = post_compare(PROMOTION_FEN, "e7e8q", "e7e8q").json()
    assert data["correct"] is True
    assert data["legal"] is True


def test_correct_queen_promotion_san_contains_queen_symbol() -> None:
    data = post_compare(PROMOTION_FEN, "e7e8q", "e7e8q").json()
    assert data["expected_san"] == "e8=Q"
    assert data["submitted_san"] == "e8=Q"


def test_incorrect_promotion_piece_is_legal_but_not_correct() -> None:
    # Submitting rook promotion when queen promotion is expected
    data = post_compare(PROMOTION_FEN, "e7e8q", "e7e8r").json()
    assert data["correct"] is False
    assert data["legal"] is True


def test_incorrect_promotion_piece_san_reflects_submitted_piece() -> None:
    data = post_compare(PROMOTION_FEN, "e7e8q", "e7e8r").json()
    assert data["submitted_san"] == "e8=R"
    assert data["expected_san"] == "e8=Q"


def test_promotion_without_piece_suffix_is_illegal() -> None:
    # "e7e8" is not a valid promotion UCI — python-chess requires a piece suffix
    data = post_compare(PROMOTION_FEN, "e7e8q", "e7e8").json()
    assert data["correct"] is False
    assert data["legal"] is False
    assert data["submitted_san"] is None


# ---------------------------------------------------------------------------
# Castling
# ---------------------------------------------------------------------------


def test_correct_kingside_castling_returns_correct_true() -> None:
    data = post_compare(CASTLING_FEN, "e1g1", "e1g1").json()
    assert data["correct"] is True
    assert data["legal"] is True


def test_correct_kingside_castling_san_is_o_o() -> None:
    data = post_compare(CASTLING_FEN, "e1g1", "e1g1").json()
    assert data["expected_san"] == "O-O"
    assert data["submitted_san"] == "O-O"


def test_queenside_castling_when_kingside_expected_is_legal_but_not_correct() -> None:
    data = post_compare(CASTLING_FEN, "e1g1", "e1c1").json()
    assert data["correct"] is False
    assert data["legal"] is True


def test_queenside_castling_san_is_o_o_o() -> None:
    data = post_compare(CASTLING_FEN, "e1g1", "e1c1").json()
    assert data["submitted_san"] == "O-O-O"
    assert data["expected_san"] == "O-O"


def test_castling_when_rights_blocked_is_illegal() -> None:
    # Remove castling rights from the FEN — castling is now illegal
    no_castling_fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
    data = post_compare(no_castling_fen, "e1e2", "e1g1").json()
    assert data["legal"] is False
    assert data["correct"] is False


# ---------------------------------------------------------------------------
# Error cases — invalid FEN or expected_uci
# ---------------------------------------------------------------------------


def test_invalid_fen_returns_400() -> None:
    r = post_compare("not-a-fen", "e2e4", "e2e4")
    assert r.status_code == 400


def test_invalid_fen_response_has_detail_field() -> None:
    r = post_compare("not-a-fen", "e2e4", "e2e4")
    assert "detail" in r.json()


def test_invalid_expected_uci_returns_400() -> None:
    r = post_compare(STARTING_FEN, "e2e9", "e2e4")
    assert r.status_code == 400


def test_missing_field_returns_422() -> None:
    r = client.post("/compare", json={"fen": STARTING_FEN, "expected_uci": "e2e4"})
    assert r.status_code == 422
