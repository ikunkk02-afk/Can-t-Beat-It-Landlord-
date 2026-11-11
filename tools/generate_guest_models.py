"""Prepare modeling instructions and painted atlases for the Blockbench MCP workflow.

Blockbench creates the bones/cubes and exports the authoritative .bbmodel/.geo.json.
This script only writes construction data under build/tmp and the model textures.
"""
from pathlib import Path
from PIL import Image, ImageColor, ImageDraw
import json
import math

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/bosslandlord"
OUTPUT = ROOT / "build/tmp/design-1.5"
SIZE, DENSITY = 512, 4


def design(hat):
    parts = {}
    skin = "#c99376" if hat else "#bd865f"
    light = "#d4a083" if hat else "#cc976e"
    shade = "#b57e65" if hat else "#aa7453"
    cloth = "#272b33" if hat else "#858d9c"
    seam = "#353a43" if hat else "#a0a7b5"

    def part(name, pivot=(0, 0, 0), parent=None):
        parts[name] = dict(pivot=pivot, parent=parent, cubes=[])

    def cube(bone, name, xyz, size, color, paint="", angle=0, axis="z", pivot=None):
        parts[bone]["cubes"].append(dict(name=name, xyz=xyz, size=size, color=color,
                                        paint=paint, angle=angle, axis=axis, pivot=pivot))

    part("root")
    part("torso", parent="root")
    cube("torso", "Upper torso", (-5.2, 1.5, -2.5), (10.4, 5.1, 5), skin, "skin")
    cube("torso", "Lower ribs", (-4.65, 6, -2.25), (9.3, 2.5, 4.5), skin)
    cube("torso", "Abdomen", (-4.3 if hat else -3.9, 7.2, -2.35), (8.6 if hat else 7.8, 4.5, 4.7),
         light, "abdomen_soft" if hat else "abdomen")
    cube("torso", "Back centre", (-2.5, 2.3, 2.2), (5, 6.5, .5), skin, "back")
    cube("torso", "Neck", (-1.75, -.7, -1.6), (3.5, 3, 3.3), skin)
    for s in (-1, 1):
        cube("torso", "Trapezius", (-4.8 if s < 0 else 1.6, .75, -1.7), (3.2, 1.8, 3.5), light,
             angle=-s * 13, pivot=(s * 1.6, 1.5, 0))
        cube("torso", "Pectoral", (-4.85 if s < 0 else .2, 2.2, -2.8), (4.65, 3.2, .55), skin, "pectoral",
             angle=s * 3, pivot=(s * .2, 3.7, -2.4))
        cube("torso", "Lat", (-5.1 if s < 0 else 3.85, 4, .2), (1.25, 4.5, 2.5), skin,
             angle=s * 10, pivot=(s * 4.5, 4, .7))
    cube("torso", "Waistband", (-4.4, 11.3, -2.65), (8.8, 1, 5.3), cloth, "belt" if hat else "waistband")
    if hat:
        cube("torso", "Belt buckle", (-.8, 11.4, -2.85), (1.6, .7, .25), "#b1a792", "metal")
    else:
        for s in (-1, 1):
            cube("torso", "Drawstring", (s * .35, 12, -2.8), (.18, 2.2, .18), "#333a48",
                 angle=s * 7, pivot=(0, 12, -2.8))

    part("head", (0, .5, 0), "torso")
    cube("head", "Face", (-3, -6.6, -2.9), (6, 6, 5.8), skin, "face_hat" if hat else "face_gu")
    cube("head", "Jaw", (-2.5, -1.3, -2.6), (5, 1.1, 5), skin)
    cube("head", "Nose bridge", (-.45, -3.75, -3.2), (.9, 1.55, .55), light, "nose")
    cube("head", "Nose tip", (-.65, -2.7, -3.55), (1.3, .65, .55), light)
    for s in (-1, 1):
        cube("head", "Ear", (-3.4 if s < 0 else 3, -3.8, -.25), (.5, 1.55, 1.4), skin, "ear")
        cube("head", "Cheek", (-2.85 if s < 0 else 1.6, -2.8, -3), (1.25, 1.2, .25), skin)
    if hat:
        cube("head", "Beard chin", (-2.3, -1.45, -3.15), (4.6, 1.7, 2), "#3c2c28", "beard")
        for s in (-1, 1):
            cube("head", "Beard side", (-3.05 if s < 0 else 2.3, -2.95, -2.95), (.75, 2.1, 2.5),
                 "#44312a", "beard", angle=s * 7, pivot=(s * 2.6, -1, -1.5))
        cube("head", "Moustache", (-1.55, -1.95, -3.2), (3.1, .55, .3), "#473029", "moustache")
        cube("head", "Hair at nape", (-2.9, -4.8, 2.2), (5.8, 4, .9), "#382a26", "hair")
        cube("head", "Brim centre", (-4.1, -6.85, -3.9), (8.2, .4, 7.8), "#25272d", "felt")
        for s in (-1, 1):
            cube("head", "Brim side", (-4.8 if s < 0 else 3.8, -6.95, -2.7), (1, .4, 5.4),
                 "#292b32", "felt", angle=s * 10, pivot=(s * 3.8, -6.8, 0))
        cube("head", "Fedora crown", (-3.2, -9, -2.9), (6.4, 2.2, 5.8), "#303239", "felt")
        cube("head", "Hat ribbon", (-3.25, -7.6, -2.95), (6.5, .7, 5.9), "#15191e", "ribbon")
        for s in (-1, 1):
            cube("head", "Pinched top", (-2.8 if s < 0 else .1, -9.45, -2.55), (2.7, .65, 5.1),
                 "#383a40", "felt", angle=s * 9, pivot=(s * 2.5, -9, 0))
        cube("head", "Ribbon clasp", (3.22, -7.5, -.6), (.18, .5, 1), "#8f8373")
    else:
        cube("head", "Hair cap", (-3.07, -6.95, -2.85), (6.14, 1.2, 5.8), "#24252b", "hair")
        cube("head", "Swept hair", (-2.75, -7.45, -2.95), (5.6, .9, 5.7), "#30313a", "hair",
             angle=-6, pivot=(0, -6.7, 0))
        for s in (-1, 1):
            cube("head", "Fade", (-3.1 if s < 0 else 2.9, -5.9, -.8), (.2, 1.65, 3.7), "#403b39", "fade")
        cube("head", "Goatee", (-.95, -1.1, -2.95), (1.9, .9, .45), "#392e29", "beard")
        cube("head", "Moustache", (-1.3, -1.8, -3.01), (2.6, .3, .2), "#44332b")

    for side, s in (("right", -1), ("left", 1)):
        arm, elbow, leg, knee = (side + suffix for suffix in ("_arm", "_elbow", "_leg", "_knee"))
        part(arm, (s * 5.45, 2.7, 0), "torso")
        cube(arm, "Deltoid", (-1.9, -.65, -1.85), (3.8, 3.6, 3.7), light, "muscle", angle=-s * 11)
        cube(arm, "Upper arm", (-1.6, 2, -1.65), (3.2, 3.5, 3.3), skin, "muscle")
        cube(arm, "Bicep", (-1.2, 1.5, -1.95), (2.4, 2.7, .6), light, "muscle", angle=-8, axis="x")
        part(elbow, (0, 5.1, 0), arm)
        cube(elbow, "Forearm upper", (-1.5, -.3, -1.5), (3, 2.6, 3), skin, "muscle")
        cube(elbow, "Forearm taper", (-1.2, 1.7, -1.2), (2.4, 2.6, 2.4), skin, "muscle")
        cube(elbow, "Hand", (-1.1, 4, -1), (2.2, 2.1, 1.8), light, "hand")
        cube(elbow, "Thumb", (-1.55 if s < 0 else 1.05, 4.2, -.9), (.5, 1.4, .9), skin, angle=-s * 16)
        part(leg, (s * 2.25, 12, 0), "root")
        cube(leg, "Trouser thigh", (-2.2, 0, -2.35), (4.4, 5.7, 4.7), cloth, "trousers")
        cube(leg, "Inner trouser seam", (-2.27 if s > 0 else 2.12, .4, -.6), (.15, 4.8, 1), seam)
        if hat:
            cube(leg, "Cargo pocket", (-2.6 if s < 0 else 2.1, 1.2, -1.5), (.5, 2.9, 3), seam, "pocket")
            cube(leg, "Cargo flap", (-2.7 if s < 0 else 2.05, 1, -1.6), (.65, .65, 3.2), "#42464c")
        part(knee, (0, 5.4, 0), leg)
        cube(knee, "Calf", (-1.95, 0, -2.1), (3.9, 3.8, 4.2), cloth, "trousers")
        cube(knee, "Cuff", (-1.65, 3.4, -1.75), (3.3, 1.35, 3.5), seam, "cuff")
        cube(knee, "Boot" if hat else "Foot", (-1.8, 4.5, -3.1), (3.6, 1.8, 5.1),
             "#2c2a2c" if hat else skin, "boot" if hat else "foot")
        if hat:
            cube(knee, "Boot sole", (-1.85, 5.8, -3.15), (3.7, .4, 5.2), "#191c23", "sole")
    return parts


def paint(image, rect, color, pattern, face):
    u, v, w, h = rect
    base = ImageColor.getrgb(color)
    def rgb(delta):
        return tuple(max(0, min(255, c + int(delta))) for c in base) + (255,)
    for y in range(h):
        for x in range(w):
            edge = min(x, w - 1 - x) / max(1, w / 2)
            light = 4 * math.sin(edge * math.pi / 2) - 6 * y / max(1, h) + (3 if face == "up" else 0)
            image.putpixel((u + x, v + y), rgb(light))
    d = ImageDraw.Draw(image)
    def line(x1, y1, x2, y2, fill, width=1):
        d.line((u+x1, v+y1, u+x2, v+y2), fill=fill, width=width)
    if pattern.startswith("face") and face == "north":
        for x in (4, 15):
            line(x, 8, x+5, 8, "#583d32", 2)
            line(x, 10, x+4, 10, "#eee1d0", 2)
            line(x+2, 10, x+2, 11, "#322c2d", 2)
            line(x, 13, x+4, 13, rgb(-12))
        line(10, 16, 13, 16, rgb(-28))
        line(9, 20, 14, 20, "#85594f")
        line(10, 21, 13, 21, "#d29a81")
    elif pattern in ("abdomen", "abdomen_soft") and face == "north":
        strength = -19 if pattern == "abdomen" else -9
        for y in (3, 8, 13):
            line(5, y, w//2-2, y+1, rgb(strength))
            line(w//2+2, y+1, w-6, y, rgb(strength))
        line(w//2, 1, w//2, h-3, rgb(-12))
        line(w//2-1, h-4, w//2, h-4, rgb(-32))
    elif pattern == "pectoral" and face == "north":
        line(3, h-3, w-4, h-2, rgb(-18))
        line(w//2, h//2+1, w//2+1, h//2+1, rgb(-35), 2)
    elif pattern in ("beard", "hair", "fade", "moustache"):
        for x in range(1, w-1, 3):
            line(x, 1, max(0,x-2), h-2, rgb(5 if x % 2 else -8))
    elif pattern in ("trousers", "cuff", "pocket", "boot"):
        line(1, 1, 1, h-2, rgb(12))
        line(w-2, 1, w-2, h-2, rgb(-13))
        for y in range(4, h-2, 8):
            line(2, y, min(w-2, 7), y+2, rgb(-8))
        if pattern == "boot" and face == "north":
            for y in range(2, h-1, 2): line(4, y, w-5, y, "#8e847c")
    elif pattern == "back" and face == "south":
        line(w//2, 2, w//2, h-3, rgb(-17))
    elif pattern in ("hand", "foot") and face == "north":
        for x in range(3, w-2, 3): line(x, h//2, x, h-2, rgb(-18))


def build(identifier, parts):
    atlas = Image.new("RGBA", (SIZE, SIZE))
    cubes = [c for p in parts.values() for c in p["cubes"]]
    u = v = row = 2
    for c in sorted(cubes, key=lambda c: c["size"][1]+c["size"][2], reverse=True):
        w,h,d = [max(1, round(n*DENSITY)) for n in c["size"]]
        if u + 2*(w+d)+2 > SIZE: u,v,row = 2,v+row,2
        if v + h+d+2 > SIZE: raise ValueError("Atlas overflow")
        rects = dict(north=(u+d,v+d,w,h), south=(u+2*d+w,v+d,w,h),
                     east=(u+d+w,v+d,d,h), west=(u,v+d,d,h),
                     up=(u+d,v,w,d), down=(u+d+w,v,w,d))
        c["faces"] = {}
        for face, rect in rects.items():
            paint(atlas, rect, c["color"], c["paint"], face)
            x,y,a,b = rect
            c["faces"][face] = {"uv":[x,y,x+a,y+b]}
        u += 2*(w+d)+2
        row = max(row,h+d+2)
    target = ASSETS / f"textures/entity/guest/{identifier}.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(target)
    # The coordinates are converted from the model's downward Y axis to Blockbench's upward Y axis.
    positions, groups, instructions = {}, [], []
    for name,p in parts.items():
        parent = positions.get(p["parent"], (0,0,0))
        pos = tuple(a+b for a,b in zip(parent,p["pivot"]))
        positions[name] = pos
        ox,oy,oz = pos
        groups.append(dict(name=name, parent=p["parent"], origin=[-ox,24-oy,oz]))
        for c in p["cubes"]:
            x,y,z=c["xyz"]; w,h,d=c["size"]
            pivot=c["pivot"] or (0,0,0)
            origin=[-ox-pivot[0],24-oy-pivot[1],oz+pivot[2]]
            entry=dict(name=c["name"],group=name,box_uv=False,
                       **{"from":[-ox-x-w,24-oy-y-h,oz+z]},to=[-ox-x,24-oy-y,oz+z+d],origin=origin,faces=c["faces"])
            if c["angle"]:
                entry["rotation"] = dict(angle=-c["angle"] if c["axis"]!="z" else c["angle"],axis=c["axis"],origin=origin)
            instructions.append(entry)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / f"{identifier}.construction.json").write_text(json.dumps(dict(groups=groups,cubes=instructions)),encoding="utf-8")


if __name__ == "__main__":
    build("naochi", design(True))
    build("gu_zhenxing", design(False))
