#!/usr/bin/env python3
"""Validate user's lean-bulk prescription before producing an Android APK."""
import json
from pathlib import Path

source = Path(__file__).resolve().parent.parent / "workouts" / "plan.json"
plan = json.loads(source.read_text(encoding="utf-8"))
assert plan["schemaVersion"] == 1
days = {day["id"]: day for day in plan["days"]}
assert list(days) == ["mon", "wed", "fri", "bonus"]
expected = {"mon": (20, 0), "wed": (19, 0), "fri": (19, 0), "bonus": (8, 0)}
fallback = source.parent.parent / "app" / "src" / "main" / "assets" / "workout-plan.json"
assert json.loads(fallback.read_text(encoding="utf-8")) == plan, "offline fallback differs from GitHub plan"
assert plan["revision"] == "2026-10-27.1"
allowed = {"traps", "shoulders", "rear_delts", "chest", "biceps",
           "triceps", "forearms", "lats", "upper_back", "lower_back",
           "abs", "obliques", "quads", "hamstrings", "glutes", "calves"}
for key, day in days.items():
    ids = [ex["id"] for ex in day["exercises"]]
    assert len(ids) == len(set(ids)), f"duplicate exercise IDs in {key}"
    main = sum(e["sets"] for e in day["exercises"] if not e.get("optional"))
    opt = sum(e["sets"] for e in day["exercises"] if e.get("optional"))
    assert (main, opt) == expected[key], (key, main, opt)
    assert 1 <= len(ids) <= 30
    assert day.get("cooldown"), f"missing post-workout walk in {key}"
    for ex in day["exercises"]:
        assert 1 <= ex["sets"] <= 8
        assert 30 <= ex["restSeconds"] <= 300
        assert ex["target"].strip()
        assert ex["primaryMuscles"], ex["id"]
        assert set(ex["primaryMuscles"]+ex.get("secondaryMuscles", [])) <= allowed
assert not days["mon"]["optional"] and not days["wed"]["optional"] and not days["fri"]["optional"]
assert days["bonus"]["optional"]
assert [e["id"] for e in days["fri"]["exercises"] if e.get("timeBased")] == []
assert not [e["id"] for d in ("mon", "wed", "fri") for e in days[d]["exercises"] if e.get("optional")]
assert {"mon_chest_recall", "mon_lat_recall"} <= {
    e["id"] for e in days["mon"]["exercises"]}
assert {"fri_lateral_recall", "fri_leg_press_recall", "fri_leg_curl_recall"} <= {
    e["id"] for e in days["fri"]["exercises"]}
assert len({e["id"] for d in days.values() for e in d["exercises"]}) == sum(
    len(d["exercises"]) for d in days.values())
assert sum(e["sets"] for e in days["mon"]["exercises"] if "chest" in e["primaryMuscles"]) == 2
assert sum(e["sets"] for e in days["fri"]["exercises"] if "chest" in e["primaryMuscles"]) == 5
forearm_direct = [
    e for day in (days["mon"], days["wed"], days["fri"]) for e in day["exercises"]
    if e["id"] in ("reverse_curl_ez", "wrist_curl", "reverse_wrist_curl", "farmers_walk")
]
assert sum(e["sets"] for e in forearm_direct) == 5
print("Lean-bulk program OK · sets:", expected, "· direct forearm sets:", 5)
