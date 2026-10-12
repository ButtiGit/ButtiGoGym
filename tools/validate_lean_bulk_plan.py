#!/usr/bin/env python3
"""Validate user's lean-bulk prescription before producing an Android APK."""
import json
from pathlib import Path

source = Path(__file__).resolve().parent.parent / "workouts" / "plan.json"
plan = json.loads(source.read_text(encoding="utf-8"))
assert plan["schemaVersion"] == 1
days = {day["id"]: day for day in plan["days"]}
assert list(days) == ["mon", "wed", "fri", "bonus"]
expected = {"mon": (22, 2), "wed": (21, 0), "fri": (19, 2), "bonus": (8, 0)}
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
assert [e["id"] for e in days["fri"]["exercises"] if e.get("timeBased")] == ["farmers_walk"]
assert [e["id"] for e in days["mon"]["exercises"] if e.get("optional")] == ["mon_chest_recall"]
assert [e["id"] for e in days["fri"]["exercises"] if e.get("optional")] == ["fri_lateral_recall"]
forearm_direct = [
    e for day in (days["mon"], days["fri"]) for e in day["exercises"]
    if e["id"] in ("reverse_curl_ez", "wrist_curl", "reverse_wrist_curl", "farmers_walk")
]
assert sum(e["sets"] for e in forearm_direct) == 8
print("Lean-bulk program OK · sets:", expected, "· direct forearm sets:", 8)
