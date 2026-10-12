#!/usr/bin/env python3
"""Vendor a pinned, MIT-licensed photoreal MuscleMap body map for offline Android use.

Upstream: https://github.com/Jsplice/MuscleMap
Source assets are hand-traced anatomical surfaces; do not redraw their geometry.
"""
from __future__ import annotations

import html
import pathlib
import re
import urllib.request

UPSTREAM = "https://raw.githubusercontent.com/Jsplice/MuscleMap"
REVISION = "170cfacda79376dd59ac0cf2f7dfab8ff9f4443a"
ASSETS = pathlib.Path("app/src/main/assets")
ASSETS.mkdir(parents=True, exist_ok=True)

ANATOMY_TO_APP = {
    "TRAPEZIUS": "traps",
    "CHEST": "chest",
    "SHOULDERS_FRONT": "shoulders",
    "SHOULDERS_SIDE": "shoulders",
    "SHOULDERS_REAR": "rear_delts",
    "BICEPS": "biceps",
    "TRICEPS": "triceps",
    "FOREARMS": "forearms",
    "LATS": "lats",
    "RHOMBOIDS": "upper_back",
    "BACK_LOWER": "lower_back",
    "CORE": "abs",
    "OBLIQUES": "obliques",
    "GLUTES": "glutes",
    "QUADS": "quads",
    "HAMSTRINGS": "hamstrings",
    "CALVES": "calves",
    "ABDUCTORS": "glutes",
    "ADDUCTORS": "quads",
}
# Abductors/adductors participate as thigh/glute subregions in the app's
# broader 16-group model. They are not presented as separate muscle stats.

def read(path: str) -> bytes:
    url = f"{UPSTREAM}/{REVISION}/{path}"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "ButtiGoGym-Build/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as res:
        return res.read()

def records(code: str, constant: str) -> list[dict[str, str]]:
    matched = re.search(
        r"const\s+" + re.escape(constant) + r":\s*\w+\[\]\s*=\s*\[(.*?)\];",
        code,
        re.S,
    )
    if not matched:
        raise ValueError(f"Missing {constant} array")
    items = []
    for record in re.findall(r"\{\s*([^{}]+)\s*\}", matched.group(1), re.S):
        get = lambda field: re.search(
            r"\b" + re.escape(field) + r':\s*"([^"]*)"', record
        )
        d = get("d")
        if d is None:
            raise ValueError("Muscle path without SVG path data")
        entry = {
            "d": d.group(1),
            "id": (get("id").group(1) if get("id") else ""),
            "group": (get("group").group(1) if get("group") else ""),
        }
        items.append(entry)
    if not items:
        raise ValueError(f"No entries found in {constant}")
    return items

def build_diagram(side: str) -> str:
    code = read(f"packages/assets/src/male-{side}.ts").decode("utf-8")
    outlines = records(code, "OUTLINE")
    muscles = records(code, "MUSCLES")
    print(f"  {side}: {len(outlines)} body paths, {len(muscles)} muscles")
    body = "\n".join(
        f'<path d="{html.escape(p["d"], quote=True)}" class="body-shape"/>'
        for p in outlines
    )
    parts = []
    for m in muscles:
        target = ANATOMY_TO_APP.get(m["group"], "")
        if target:
            parts.append(
                f'<path class="muscle" data-muscle="{target}" '
                f'data-part="{html.escape(m["id"], quote=True)}" '
                f'd="{html.escape(m["d"], quote=True)}"/>'
            )
        else:
            parts.append(
                f'<path class="muscle-muted" d="{html.escape(m["d"], quote=True)}"/>'
            )
    return (
        f'<svg class="diagram" id="{side}" viewBox="160 20 704 1440" '
        'preserveAspectRatio="xMidYMid meet" role="img" '
        f'aria-label="Muscoli anatomici, vista {"anteriore" if side == "front" else "posteriore"}">'
        '<g>' + body + '</g>'
        f'<image href="male-{side}.webp" x="0" y="0" width="1024" height="1536" '
        'preserveAspectRatio="none" class="body-photo"/>'
        '<g class="muscle-surfaces">' + "\n".join(parts) + '</g></svg>'
    )

for side in ("front", "back"):
    image = read(f"packages/assets/bodies/male-{side}.webp")
    if not image.startswith(b"RIFF") or b"WEBP" not in image[:16]:
        raise ValueError(f"Invalid upstream WebP {side}")
    (ASSETS / f"male-{side}.webp").write_bytes(image)

license_text = read("LICENSE").decode("utf-8")
(ASSETS / "MUSCLEMAP-LICENSE.txt").write_text(
    "Source: https://github.com/Jsplice/MuscleMap\n"
    f"Revision: {REVISION}\n"
    "Anatomical traced paths and male body reference photos used under MIT.\n\n"
    + license_text,
    encoding="utf-8",
)

page = """<!doctype html>
<html lang="it"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#fff}
body{font-family:system-ui,-apple-system,Roboto,sans-serif}
.viewport{display:flex;align-items:center;justify-content:center;width:100%;height:100%}
.diagram{display:none;width:100%;height:100%}
.diagram.active{display:block}
.body-shape{fill:#e5eae6;stroke:#bec9c0;stroke-width:1.5}
.body-photo{opacity:.86;filter:grayscale(1) brightness(1.13) contrast(.92);pointer-events:none}
.muscle{fill:#b9c8bc;fill-opacity:.34;stroke:#fff;stroke-width:2.25;
  stroke-opacity:.73;stroke-linejoin:round;cursor:pointer;
  transition:fill .2s,fill-opacity .2s,stroke .2s}
.muscle-muted{fill:#d8e1da;fill-opacity:.25;stroke:#fff;stroke-opacity:.55;stroke-width:2}
.muscle.highlight{stroke:#064a12;stroke-width:5;stroke-opacity:1;fill-opacity:.88}
</style></head><body><div class="viewport">
%%DIAGRAMS%%
</div><script>
"use strict";
const ALLOWED=new Set([
"traps","shoulders","rear_delts","chest","biceps","triceps","forearms",
"lats","upper_back","lower_back","abs","obliques","quads",
"hamstrings","glutes","calves"
]);
const palette={NEVER:["#dbe4de",.20],TRAINED_TODAY:["#106119",.83],
RECOVERING:["#ffb85c",.82],READY:["#a8d9b0",.71]};
let state={}, selected="", view="front";
function redraw(){
 document.querySelectorAll(".diagram").forEach(node=>{
   node.classList.toggle("active",node.id===view);
 });
 document.querySelectorAll(".muscle").forEach(node=>{
   const group=node.dataset.muscle, style=palette[state[group]]||palette.NEVER;
   node.style.fill=style[0];
   node.style.fillOpacity=String(style[1]);
   node.classList.toggle("highlight",group===selected);
 });
}
window.updateMuscleMap=function(next, selectedId, side){
 state=next||{};
 selected=ALLOWED.has(selectedId)?selectedId:"";
 view=side==="back"?"back":"front";
 redraw();
};
document.addEventListener("click",function(event){
 const hit=event.target.closest(".muscle");
 if(!hit)return;
 const group=hit.dataset.muscle;
 if(!ALLOWED.has(group))return;
 selected=group;redraw();
 if(window.AndroidMuscles&&window.AndroidMuscles.selectMuscle){
   window.AndroidMuscles.selectMuscle(group);
 }
});
redraw();
</script></body></html>
"""
rendered = page.replace(
    "%%DIAGRAMS%%", build_diagram("front") + build_diagram("back")
)
(ASSETS / "muscle-map.html").write_text(rendered, encoding="utf-8")

# Native Android renderer: lossless paths from the original template. This
# avoids Android WebView entirely, which rendered a blank panel on some devices.
import json
native_diagrams = {}
for side in ("front", "back"):
    code = read(f"packages/assets/src/male-{side}.ts").decode("utf-8")
    native_diagrams[side] = [
        {
            "muscle": ANATOMY_TO_APP[part["group"]],
            "part": part["id"],
            "path": part["d"]
        }
        for part in records(code, "MUSCLES")
        if part["group"] in ANATOMY_TO_APP
    ]
native_output = {"source": "Jsplice/MuscleMap", "revision": REVISION, "views": native_diagrams}
(ASSETS / "muscle-map-regions.json").write_text(
    json.dumps(native_output, ensure_ascii=False, separators=(",", ":")),
    encoding="utf-8"
)
assert len(native_diagrams["front"]) >= 20
assert len(native_diagrams["back"]) >= 20
print("Generated native muscle paths:", {k: len(v) for k, v in native_diagrams.items()})
print("Generated bundled realistic interactive MuscleMap", len(rendered), "chars")
