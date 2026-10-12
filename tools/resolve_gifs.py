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
    rejected = []
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
            # Reject close-looking but biomechanically different demonstrations.
            label = exercise.get("id", "")
            def compatible(name):
                name = normalize(name)
                if label == "leg_press": return "leg press" in name and "one leg" not in name and "single leg" not in name
                if label == "leg_extension": return "leg extension" in name and "triceps" not in name
                if label == "leg_curl": return "leg curl" in name and "seated" in name
                if label == "crunch": return "crunch" in name and "side" not in name and "oblique" not in name
                if label == "split_squat": return "bulgarian" in name and "step up" not in name
                if label == "bench": return "chest press" in name and "standing" not in name
                if label in ("tricep_pushdown", "bonus_pushdown"): return "pushdown" in name and "rope" in name
                return True
            candidates = [(v, item) for v, item in options if compatible(item.get("name", ""))]
            print("Candidate GIFs for", day["id"], label,
                  [(round(v, 2), item["name"]) for v, item in candidates[:3]])
            if not candidates:
                missing.append(query)
                continue
            best_score, best = candidates[0]
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
