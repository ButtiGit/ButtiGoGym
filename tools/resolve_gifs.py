#!/usr/bin/env python3
"""Match the remote exercise GIF archive and update only missing GIF URLs.

The GIF files stay on the source author's GitHub repository: we do not redistribute them.
Review the matches in the build log before publishing the plan.
"""
import difflib
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLAN_FILE = ROOT / "workouts" / "plan.json"
CATALOG_URL = "https://raw.githubusercontent.com/mohamedatef90/exercise-library/main/exercises.json"
GIF_BASE = "https://raw.githubusercontent.com/mohamedatef90/exercise-library/main/gifs/"

def normalize(value):
    value = str(value).lower()
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return " ".join(value.split())

def score(query, name):
    a, b = normalize(query), normalize(name)
    a_tokens, b_tokens = set(a.split()), set(b.split())
    overlap = len(a_tokens & b_tokens) / max(1, len(a_tokens))
    ratio = difflib.SequenceMatcher(None, a, b).ratio()
    exact_bonus = 0.35 if a == b else 0.15 if a in b else 0
    return overlap * 0.55 + ratio * 0.45 + exact_bonus

def main():
    with urllib.request.urlopen(CATALOG_URL, timeout=30) as stream:
        library = json.load(stream)
    if isinstance(library, dict):
        library = library.get("exercises", [])
    assert len(library) >= 500, "The GIF catalog was not downloaded correctly"

    plan = json.loads(PLAN_FILE.read_text(encoding="utf-8"))
    total = 0
    missing = []
    for day in plan["days"]:
        for exercise in day["exercises"]:
            query = exercise.get("gifQuery", "").strip()
            if not query or exercise.get("gifUrl"):
                continue
            options = sorted(
                ((score(query, item.get("name", "")), item) for item in library
                 if item.get("name") and item.get("gif")),
                key=lambda v: v[0], reverse=True
            )
            best_score, best = options[0]
            print(f"{day['id']:>5} | {query:34} -> {best.get('name','')[:42]:42} | {best_score:.2f}")
            if best_score < 0.66:
                missing.append(query)
                continue
            filename = best["gif"]
            assert filename.endswith(".gif") and "/" not in filename
            exercise["gifUrl"] = GIF_BASE + filename
            total += 1
    PLAN_FILE.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Resolved {total} GIF references. Missing: {missing}")
    if missing:
        print("Unmatched exercises use their written instructions rather than a misleading GIF.")

if __name__ == "__main__":
    main()
