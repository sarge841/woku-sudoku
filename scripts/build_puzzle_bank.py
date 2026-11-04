#!/usr/bin/env python3
"""Rebuild Woku's bundled, technique-rated puzzle bank.

Source: Sudoku Exchange "Puzzle Bank" by Grant McLean
  https://github.com/grantm/sudoku-exchange-puzzle-bank
  Dedicated to the public domain (Unlicense, see its LICENSE.txt).
  Puzzles were generated with QQWing and rated with Sukaku Explainer (SE scale).

Output (one puzzle per line: "<81 digits> <rating>"), committed to
app/src/main/resources/com/sarge841/woku/puzzles/:

  expert.txt           SE 4.0-5.9  (3000 from hard.txt 4.x + 2000 from diabolical.txt 5.x)
  extreme.txt          SE 6.0-6.9
  diabolical.txt       SE 7.0-8.4
  diabolical_plus.txt  SE 8.5+     (every 9.x puzzle is kept)

Each puzzle is re-verified to have exactly one solution. The selection is seeded,
so re-running produces identical files. Downloads are cached in a temp directory,
never in the repository.

Usage:  python3 scripts/build_puzzle_bank.py
"""
import os
import random
import sys
import tempfile
import urllib.request

BASE = "https://github.com/grantm/sudoku-exchange-puzzle-bank/raw/master/"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "resources",
                   "com", "sarge841", "woku", "puzzles")
CACHE = os.path.join(tempfile.gettempdir(), "woku-puzzle-bank-cache")
PER_TIER = 5000
SEED = 841


def fetch(name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        print(f"downloading {name}…", file=sys.stderr)
        urllib.request.urlretrieve(BASE + name, path)
    rows = []
    with open(path) as f:
        for line in f:
            parts = line.split()
            if len(parts) == 3 and len(parts[1]) == 81:
                rows.append((parts[1], float(parts[2])))
    return rows


PEERS = []
for i in range(81):
    r, c = divmod(i, 9)
    br, bc = r // 3 * 3, c // 3 * 3
    p = {r * 9 + k for k in range(9)} | {k * 9 + c for k in range(9)}
    p |= {(br + a) * 9 + bc + b for a in range(3) for b in range(3)}
    p.discard(i)
    PEERS.append(tuple(p))


def count_solutions(puzzle, limit=2):
    board = [int(ch) for ch in puzzle]
    for i, v in enumerate(board):
        if v and any(board[p] == v for p in PEERS[i]):
            return 0

    def search():
        best, best_opts = -1, None
        for i in range(81):
            if board[i] == 0:
                used = {board[p] for p in PEERS[i]}
                opts = [d for d in range(1, 10) if d not in used]
                if not opts:
                    return 0
                if best_opts is None or len(opts) < len(best_opts):
                    best, best_opts = i, opts
                    if len(opts) == 1:
                        break
        if best == -1:
            return 1
        total = 0
        for d in best_opts:
            board[best] = d
            total += search()
            board[best] = 0
            if total >= limit:
                break
        return total

    return search()


def pick(rng, rows, n):
    rows = sorted(set(rows))
    return rows if len(rows) <= n else rng.sample(rows, n)


def main():
    rng = random.Random(SEED)
    hard = fetch("hard.txt")
    diabolical = fetch("diabolical.txt")
    band = lambda rows, lo, hi: [r for r in rows if lo <= r[1] < hi]

    plus_all = band(diabolical, 8.5, 99)
    nines = band(plus_all, 9.0, 99)
    tiers = {
        "expert": pick(rng, band(hard, 4.0, 5.0), 3000) + pick(rng, band(diabolical, 5.0, 6.0), 2000),
        "extreme": pick(rng, band(diabolical, 6.0, 7.0), PER_TIER),
        "diabolical": pick(rng, band(diabolical, 7.0, 8.5), PER_TIER),
        "diabolical_plus": nines + pick(rng, band(plus_all, 8.5, 9.0), PER_TIER - len(nines)),
    }
    os.makedirs(OUT, exist_ok=True)
    for name, rows in tiers.items():
        rows = sorted(rows, key=lambda r: (r[1], r[0]))
        bad = [p for p, _ in rows if count_solutions(p) != 1]
        if bad:
            sys.exit(f"{name}: {len(bad)} puzzles without a unique solution")
        with open(os.path.join(OUT, f"{name}.txt"), "w", newline="\n") as f:
            for p, rating in rows:
                f.write(f"{p} {rating:.1f}\n")
        print(f"{name}: {len(rows)} puzzles, SE {rows[0][1]:.1f}-{rows[-1][1]:.1f}", file=sys.stderr)


if __name__ == "__main__":
    main()
