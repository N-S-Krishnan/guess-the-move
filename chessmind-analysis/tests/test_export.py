import chess
import chess.pgn
import io
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

SIMPLE_PGN = (
    '[Event "Test"]\n'
    '[White "Alice"]\n'
    '[Black "Bob"]\n'
    '[Result "*"]\n'
    "\n"
    "1. e4 e5 2. Nf3 Nc6 *\n"
)

# FENs produced by python-chess for each position in SIMPLE_PGN.
# python-chess omits the en passant square when no enemy pawn can capture.
_STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
_AFTER_E4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
_AFTER_E5 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
_AFTER_NF3 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"
_AFTER_NC6 = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"

# FENs for variation move sequences used in chained-variation tests
_AFTER_D4 = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1"
_AFTER_D4_D5 = "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2"


def _parse_exported(pgn_text: str) -> chess.pgn.Game:
    game = chess.pgn.read_game(io.StringIO(pgn_text))
    assert game is not None
    return game


def post_export(pgn_raw: str, annotations: list[dict]) -> dict:
    return client.post("/export", json={"pgn_raw": pgn_raw, "annotations": annotations})


# ---------------------------------------------------------------------------
# Happy path — no annotations
# ---------------------------------------------------------------------------


def test_export_no_annotations_returns_200() -> None:
    r = post_export(SIMPLE_PGN, [])
    assert r.status_code == 200


def test_export_no_annotations_contains_pgn_key() -> None:
    data = post_export(SIMPLE_PGN, []).json()
    assert "pgn" in data
    assert isinstance(data["pgn"], str)
    assert len(data["pgn"]) > 0


def test_export_no_annotations_pgn_is_valid() -> None:
    data = post_export(SIMPLE_PGN, []).json()
    game = _parse_exported(data["pgn"])
    moves = list(game.mainline_moves())
    assert len(moves) == 4  # e4 e5 Nf3 Nc6


def test_export_no_annotations_preserves_headers() -> None:
    data = post_export(SIMPLE_PGN, []).json()
    game = _parse_exported(data["pgn"])
    assert game.headers["White"] == "Alice"
    assert game.headers["Black"] == "Bob"
    assert game.headers["Event"] == "Test"


# ---------------------------------------------------------------------------
# Comments
# ---------------------------------------------------------------------------


def test_export_with_comment_returns_200() -> None:
    ann = {"fen": _AFTER_E4, "comment": "A solid opening move."}
    assert post_export(SIMPLE_PGN, [ann]).status_code == 200


def test_export_comment_appears_in_pgn_text() -> None:
    ann = {"fen": _AFTER_E4, "comment": "A solid opening move."}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "A solid opening move." in data["pgn"]


def test_export_comment_attached_to_correct_node() -> None:
    ann = {"fen": _AFTER_NF3, "comment": "Development first."}
    data = post_export(SIMPLE_PGN, [ann]).json()
    game = _parse_exported(data["pgn"])
    nodes = list(game.mainline())
    nf3_node = nodes[2]  # 0=e4, 1=e5, 2=Nf3
    assert "Development first." in nf3_node.comment


def test_export_multiple_comments_all_present() -> None:
    anns = [
        {"fen": _AFTER_E4, "comment": "First comment."},
        {"fen": _AFTER_NF3, "comment": "Second comment."},
    ]
    data = post_export(SIMPLE_PGN, anns).json()
    assert "First comment." in data["pgn"]
    assert "Second comment." in data["pgn"]


# ---------------------------------------------------------------------------
# Symbols / NAGs
# ---------------------------------------------------------------------------


def test_export_good_move_symbol_renders_as_nag_1() -> None:
    ann = {"fen": _AFTER_E4, "symbol": "!"}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "$1" in data["pgn"]


def test_export_mistake_symbol_renders_as_nag_2() -> None:
    ann = {"fen": _AFTER_E5, "symbol": "?"}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "$2" in data["pgn"]


def test_export_brilliant_symbol_renders_as_nag_3() -> None:
    ann = {"fen": _AFTER_E4, "symbol": "!!"}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "$3" in data["pgn"]


def test_export_blunder_symbol_renders_as_nag_4() -> None:
    ann = {"fen": _AFTER_E4, "symbol": "??"}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "$4" in data["pgn"]


def test_export_speculative_symbol_renders_as_nag_5() -> None:
    ann = {"fen": _AFTER_E4, "symbol": "!?"}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "$5" in data["pgn"]


def test_export_dubious_symbol_renders_as_nag_6() -> None:
    ann = {"fen": _AFTER_E4, "symbol": "?!"}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "$6" in data["pgn"]


def test_export_comment_and_symbol_both_present() -> None:
    ann = {"fen": _AFTER_E4, "comment": "Strong centre.", "symbol": "!"}
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "Strong centre." in data["pgn"]
    assert "$1" in data["pgn"]


# ---------------------------------------------------------------------------
# Variation lines
# ---------------------------------------------------------------------------


def test_export_variation_appears_in_pgn() -> None:
    # 1. d4 as an alternative to 1. e4 from the starting position
    ann = {
        "fen": _AFTER_D4,
        "from_fen": _STARTING_FEN,
        "move_uci": "d2d4",
        "move_san": "d4",
    }
    data = post_export(SIMPLE_PGN, [ann]).json()
    assert "(" in data["pgn"]
    assert "d4" in data["pgn"]


def test_export_variation_pgn_is_parseable() -> None:
    ann = {
        "fen": _AFTER_D4,
        "from_fen": _STARTING_FEN,
        "move_uci": "d2d4",
        "move_san": "d4",
    }
    data = post_export(SIMPLE_PGN, [ann]).json()
    game = _parse_exported(data["pgn"])
    # Root node should have two children: e4 (mainline) and d4 (variation)
    assert len(game.variations) == 2


def test_export_chained_variation() -> None:
    # 1. d4 d5 as a two-move variation from the starting position
    anns = [
        {
            "fen": _AFTER_D4,
            "from_fen": _STARTING_FEN,
            "move_uci": "d2d4",
            "move_san": "d4",
        },
        {
            "fen": _AFTER_D4_D5,
            "from_fen": _AFTER_D4,
            "move_uci": "d7d5",
            "move_san": "d5",
        },
    ]
    data = post_export(SIMPLE_PGN, anns).json()
    assert "d5" in data["pgn"]


def test_export_chained_variation_both_moves_parseable() -> None:
    anns = [
        {
            "fen": _AFTER_D4,
            "from_fen": _STARTING_FEN,
            "move_uci": "d2d4",
            "move_san": "d4",
        },
        {
            "fen": _AFTER_D4_D5,
            "from_fen": _AFTER_D4,
            "move_uci": "d7d5",
            "move_san": "d5",
        },
    ]
    data = post_export(SIMPLE_PGN, anns).json()
    game = _parse_exported(data["pgn"])
    # d4 variation node should have d5 as its continuation
    d4_var = game.variations[1]
    assert d4_var.move == chess.Move.from_uci("d2d4")
    assert len(d4_var.variations) == 1
    assert d4_var.variations[0].move == chess.Move.from_uci("d7d5")


# ---------------------------------------------------------------------------
# Error cases
# ---------------------------------------------------------------------------


def test_export_invalid_pgn_returns_400() -> None:
    r = post_export("this is not a pgn", [])
    assert r.status_code == 400


def test_export_invalid_pgn_response_has_detail() -> None:
    r = post_export("this is not a pgn", [])
    assert "detail" in r.json()


def test_export_missing_pgn_raw_returns_422() -> None:
    r = client.post("/export", json={"annotations": []})
    assert r.status_code == 422


def test_export_illegal_variation_move_is_silently_skipped() -> None:
    # e2e5 is not a legal move; it should be silently ignored
    ann = {
        "fen": "whatever",
        "from_fen": _STARTING_FEN,
        "move_uci": "e2e5",
        "move_san": "e5",
    }
    r = post_export(SIMPLE_PGN, [ann])
    assert r.status_code == 200
