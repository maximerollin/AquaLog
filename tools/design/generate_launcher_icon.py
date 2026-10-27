"""Generate AquaLog launcher resources (Android vector XML + Play SVG) from one geometry.

Usage: python3 tools/design/generate_launcher_icon.py androidApp/src/main/res build/icon
Render the Play Store PNG from build/icon/play-icon.svg at 512x512 (for example with headless Chrome)
and save it as docs/store/aqualog-play-icon-512.png.
"""
import os, sys
RES = sys.argv[1]; OUT = sys.argv[2]
BG, WATER, SAND, SAGE, IVORY = "#122622", "#14675e", "#cfaa69", "#bad8cb", "#f6f4ec"

def circle(cx, cy, r):
    return f"M{cx - r},{cy} A{r},{r} 0 1,0 {cx + r},{cy} A{r},{r} 0 1,0 {cx - r},{cy} Z"

WATER_P = "M29.75,46 H78.25 V71 A3.25,3.25 0 0 1 75,74.25 H33 A3.25,3.25 0 0 1 29.75,71 Z"
SAND_P = "M29.75,68.5 H78.25 V71 A3.25,3.25 0 0 1 75,74.25 H33 A3.25,3.25 0 0 1 29.75,71 Z"
PLANTS = ["M38.5,68.5 Q35,60.5 39.5,53.5", "M43,68.5 Q45.5,62.5 42,57.5"]
TANK = "M33,38 H75 A5,5 0 0 1 80,43 V71 A5,5 0 0 1 75,76 H33 A5,5 0 0 1 28,71 V43 A5,5 0 0 1 33,38 Z"
BUBBLES = circle(73.5, 53, 1.7) + " " + circle(75.5, 49, 1.1)
FISH = "M53,58 C56.2,53.6 62.6,53.6 65.8,58 C62.6,62.4 56.2,62.4 53,58 Z M65.2,58 L69.8,54.4 V61.6 Z"
EYE = circle(56.6, 57.3, 1.1)
SURFACE = "M29.75,46 H78.25"
SHIFT = -3  # centre the 28..76 tank vertically on the 108 grid

# (kind, path, colour, stroke width)
COLOR = [("fill", WATER_P, WATER, 0), ("fill", SAND_P, SAND, 0)] + \
    [("stroke", p, SAGE, 2.6) for p in PLANTS] + \
    [("stroke", TANK, SAGE, 3.5), ("fill", BUBBLES, SAGE, 0), ("fill", FISH, IVORY, 0), ("fill", EYE, BG, 0)]
MONO = [("stroke", TANK, "#FFFFFFFF", 3.5), ("stroke", SURFACE, "#FFFFFFFF", 2.6)] + \
    [("stroke", p, "#FFFFFFFF", 2.6) for p in PLANTS] + [("fill", FISH, "#FFFFFFFF", 0)]

def vpath(kind, d, color, width):
    if kind == "fill":
        return f'        <path android:fillColor="{color}" android:pathData="{d}" />'
    return (f'        <path android:strokeColor="{color}" android:strokeWidth="{width}" '
            f'android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{d}" />')

def vector(size_dp, viewport, body, comment):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n{body}\n</vector>\n')

def group(layers, scale=1.0):
    attrs = f'android:translateY="{SHIFT}"'
    inner = "\n".join(vpath(*l) for l in layers)
    if scale != 1.0:
        return (f'    <group android:pivotX="54" android:pivotY="54" android:scaleX="{scale}" android:scaleY="{scale}">\n'
                f'    <group {attrs}>\n{inner}\n    </group>\n    </group>')
    return f'    <group {attrs}>\n{inner}\n    </group>'

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(text)

d = os.path.join(RES, "drawable")
write(f"{d}/ic_launcher_background.xml", vector(108, 108,
    f'    <path android:fillColor="{BG}" android:pathData="M0,0 H108 V108 H0 Z" />', "Adaptive icon background."))
write(f"{d}/ic_launcher_foreground.xml", vector(108, 108, group(COLOR),
    "Adaptive icon foreground: content stays inside the 66dp safe zone."))
write(f"{d}/ic_launcher_monochrome.xml", vector(108, 108, group(MONO), "Themed icon layer (Android 13+)."))
# Notification small icon: white silhouette, scaled to fill the 24dp glyph area.
write(f"{d}/ic_notification.xml", vector(24, 108, group(MONO, 1.55), "Notification small icon."))
# Legacy launcher icon (Android 6-7): rounded square with a slightly larger aquarium.
legacy = f'    <path android:fillColor="{BG}" android:pathData="M24,6 H84 A18,18 0 0 1 102,24 V84 A18,18 0 0 1 84,102 H24 A18,18 0 0 1 6,84 V24 A18,18 0 0 1 24,6 Z" />\n' + group(COLOR, 1.3)
write(f"{RES}/mipmap/ic_launcher.xml", vector(48, 108, legacy, "Legacy launcher icon for Android 6-7."))
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')
write(f"{RES}/mipmap-anydpi-v26/ic_launcher.xml", adaptive)
write(f"{RES}/mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

# SVG twin for the Play Store PNG (full-bleed square; Play applies its own mask).
def spath(kind, d, color, width):
    if kind == "fill":
        return f'<path fill="{color}" d="{d}"/>'
    return f'<path fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round" d="{d}"/>'
svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">'
       f'<rect width="108" height="108" fill="{BG}"/><g transform="translate(54 54) scale(1.35) translate(-54 -54) translate(0 {SHIFT})">'
       + "".join(spath(*l) for l in COLOR) + '</g></svg>')
write(f"{OUT}/play-icon.svg", svg)
print("ok")
