# /// script
# requires-python = ">=3.10"
# dependencies = ["resvg-py==0.5.0", "pillow==13.0.0", "svgpathtools==1.8.0"]
# ///
"""Generate B2 launcher vectors, legacy PNGs and store exports from scene-b2.svg.

uv run --script tools/design/generate_launcher_icon.py androidApp/src/main/res build/icon
uv run --script tools/design/generate_launcher_icon.py --export-png
"""

import argparse
from copy import deepcopy
from io import BytesIO
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import resvg_py
from PIL import Image, PngImagePlugin
from svgpathtools import parse_path


ROOT = Path(__file__).resolve().parents[2]
SVG = "http://www.w3.org/2000/svg"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ADAPTIVE_TRANSFORM = "translate(54 54) scale(.72) translate(-54 -54)"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
ET.register_namespace("", SVG)


def android_attributes(**values):
    return {f"{{{ANDROID}}}{key}": str(value) for key, value in values.items()}


def svg_element(tag, **attributes):
    return ET.Element(f"{{{SVG}}}{tag}", attributes)


def circle_path(center_x, center_y, radius_x, radius_y):
    return (
        f"M{center_x - radius_x},{center_y} "
        f"A{radius_x},{radius_y} 0 1,0 {center_x + radius_x},{center_y} "
        f"A{radius_x},{radius_y} 0 1,0 {center_x - radius_x},{center_y} Z"
    )


def path_data(element):
    tag = element.tag.rsplit("}", 1)[-1]
    if tag == "path":
        return element.attrib["d"]
    if tag == "rect":
        start_x = float(element.get("x", "0"))
        start_y = float(element.get("y", "0"))
        end_x = start_x + float(element.attrib["width"])
        end_y = start_y + float(element.attrib["height"])
        return f"M{start_x},{start_y} H{end_x} V{end_y} H{start_x} Z"
    if tag in ("circle", "ellipse"):
        return circle_path(
            float(element.get("cx", "0")), float(element.get("cy", "0")),
            float(element.get("rx", element.get("r", "0"))),
            float(element.get("ry", element.get("r", "0"))),
        )
    raise ValueError(f"Unsupported SVG shape: {tag}")


def transform_groups(parent, transform):
    transforms = re.findall(r"(\w+)\(([^)]*)\)", transform)
    for name, arguments in transforms:
        values = [float(value) for value in re.split(r"[\s,]+", arguments.strip())]
        if name == "translate":
            attributes = android_attributes(translateX=values[0], translateY=values[1] if len(values) > 1 else 0)
        elif name == "scale":
            attributes = android_attributes(scaleX=values[0], scaleY=values[1] if len(values) > 1 else values[0])
        elif name == "rotate":
            attributes = android_attributes(
                rotation=values[0], pivotX=values[1] if len(values) > 1 else 0,
                pivotY=values[2] if len(values) > 1 else 0,
            )
        else:
            raise ValueError(f"Unsupported SVG transform: {name}")
        parent = ET.SubElement(parent, "group", attributes)
    return parent


def add_paint(target, name, paint, geometry, definitions):
    if not paint.startswith("url(#"):
        target.set(f"{{{ANDROID}}}{name}", "#00000000" if paint == "none" else paint)
        return
    gradient = definitions[paint[5:-1]]
    if gradient.get("gradientUnits", "objectBoundingBox") != "objectBoundingBox":
        raise ValueError("Only objectBoundingBox gradients are supported")
    minimum_x, maximum_x, minimum_y, maximum_y = parse_path(geometry).bbox()
    attributes = android_attributes(type="linear", tileMode="clamp")
    for label, origin, extent in (
        ("X", minimum_x, maximum_x - minimum_x), ("Y", minimum_y, maximum_y - minimum_y),
    ):
        for position, endpoint in (("start", "1"), ("end", "2")):
            coordinate = gradient.get(f"{label.lower()}{endpoint}", "0" if endpoint == "1" or label == "Y" else "1")
            fraction = float(coordinate.rstrip("%")) / (100 if coordinate.endswith("%") else 1)
            attributes[f"{{{ANDROID}}}{position}{label}"] = str(origin + fraction * extent)
    attribute = ET.SubElement(target, f"{{{AAPT}}}attr", {"name": f"android:{name}"})
    output = ET.SubElement(attribute, "gradient", attributes)
    for stop in gradient:
        alpha = round(float(stop.get("stop-opacity", "1")) * 255)
        color = stop.attrib["stop-color"].removeprefix("#")
        offset = stop.attrib["offset"]
        ET.SubElement(output, "item", android_attributes(
            color=f"#{alpha:02x}{color}",
            offset=float(offset.rstrip("%")) / (100 if offset.endswith("%") else 1),
        ))


def convert_element(element, parent, definitions, inherited=None, opacity=1.0):
    style = dict(inherited or {"fill": "#000000", "stroke": "none"})
    for key in ("fill", "stroke", "fill-opacity", "stroke-opacity", "stroke-width", "stroke-linecap", "stroke-linejoin"):
        if key in element.attrib:
            style[key] = element.attrib[key]
    opacity *= float(element.get("opacity", "1"))
    parent = transform_groups(parent, element.get("transform", ""))
    tag = element.tag.rsplit("}", 1)[-1]
    if tag == "use":
        convert_element(definitions[element.attrib["href"].removeprefix("#")], parent, definitions, style, opacity)
        return
    if tag == "g":
        for child in element:
            convert_element(child, parent, definitions, style, opacity)
        return
    geometry = path_data(element)
    target = ET.SubElement(parent, "path", android_attributes(pathData=geometry))
    for paint_type in ("fill", "stroke"):
        add_paint(target, f"{paint_type}Color", style[paint_type], geometry, definitions)
        target.set(f"{{{ANDROID}}}{paint_type}Alpha", str(opacity * float(style.get(f"{paint_type}-opacity", "1"))))
    if style["stroke"] != "none":
        for source, destination, default in (
            ("stroke-width", "strokeWidth", "1"), ("stroke-linecap", "strokeLineCap", "butt"),
            ("stroke-linejoin", "strokeLineJoin", "miter"),
        ):
            target.set(f"{{{ANDROID}}}{destination}", style.get(source, default))


def write_xml(destination, element):
    destination.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(element, space="    ")
    ET.ElementTree(element).write(destination, encoding="utf-8", xml_declaration=True)


def write_vector(destination, elements, definitions, size=108, viewport=108):
    vector = ET.Element("vector", android_attributes(
        width=f"{size}dp", height=f"{size}dp", viewportWidth=viewport, viewportHeight=viewport,
    ))
    for element in elements:
        convert_element(element, vector, definitions)
    write_xml(destination, vector)


def render_png(scene, destination, size, mode="RGBA", opaque=True):
    metadata = PngImagePlugin.PngInfo()
    metadata.add(b"sRGB", b"\x00")
    rendered = resvg_py.svg_to_bytes(
        svg_string=ET.tostring(scene, encoding="unicode"), width=size, height=size, skip_system_fonts=True,
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(BytesIO(rendered)) as image:
        if opaque and image.convert("RGBA").getchannel("A").getextrema() != (255, 255):
            raise ValueError(f"{destination}: store artwork must be fully opaque")
        image.convert(mode).save(destination, optimize=True, pnginfo=metadata)
    with Image.open(destination) as image:
        image.load()
        if image.size != (size, size) or image.mode != mode:
            raise ValueError(f"{destination}: unexpected PNG dimensions or mode")
    if destination.name == "aqualog-play-icon-512.png" and destination.stat().st_size > 1024 * 1024:
        raise ValueError("Play Store icon exceeds 1024 KB")
    print(f"{destination}: {size}x{size}, {mode}")


def export_store(scene, output):
    for filename, size, mode in (
        ("aqualog-play-icon-512.png", 512, "RGBA"),
        ("aqualog-app-store-icon-1024.png", 1024, "RGB"),
        ("aqualog-revenuecat-icon-512.png", 512, "RGBA"),
        ("aqualog-icon-256.png", 256, "RGBA"),
        ("aqualog-icon-master-2048.png", 2048, "RGB"),
    ):
        render_png(scene, output / filename, size, mode)


def fish_silhouette(definitions):
    fish = svg_element("g")
    for element in definitions["fish"]:
        if element.tag == f"{{{SVG}}}path" and element.get("fill") != "none":
            shape = deepcopy(element)
            shape.set("fill", "#ffffff")
            fish.append(shape)
    return fish


def generate_android(scene, definitions, resources, output):
    background = definitions["background"]
    foreground = deepcopy(definitions["foreground"])
    foreground.set("transform", ADAPTIVE_TRANSFORM)
    monochrome = svg_element("g", transform=ADAPTIVE_TRANSFORM)
    fish = fish_silhouette(definitions)
    fish.set("transform", "translate(52 58) scale(.95)")
    monochrome.append(fish)
    plant = next(element for element in definitions["foreground"] if element.get("fill") == "url(#leaf)")
    plant = deepcopy(plant)
    plant.set("fill", "#ffffff")
    monochrome.append(plant)
    bubbles = svg_element("g", transform="translate(29 50) scale(.9)")
    for element in definitions["bubbles"]:
        if element.get("stroke"):
            bubble = deepcopy(element)
            bubble.attrib.pop("fill-opacity", None)
            bubble.attrib.pop("stroke", None)
            bubble.attrib.pop("stroke-width", None)
            bubble.set("fill", "#ffffff")
            bubbles.append(bubble)
    monochrome.append(bubbles)
    sand = next(element for element in definitions["foreground"] if element.get("fill") == "url(#sand)")
    monochrome.append(svg_element("path", d=sand.attrib["d"], fill="none", stroke="#ffffff", **{"stroke-width": "2"}))
    for name, layer in (("background", background), ("foreground", foreground), ("monochrome", monochrome)):
        write_vector(resources / "drawable" / f"ic_launcher_{name}.xml", [layer], definitions)
    notification = fish_silhouette(definitions)
    notification.set("transform", "translate(22 25)")
    write_vector(resources / "drawable/ic_notification.xml", [notification], definitions, size=24, viewport=48)
    for name in ("ic_launcher", "ic_launcher_round"):
        adaptive = ET.Element("adaptive-icon")
        for layer in ("background", "foreground", "monochrome"):
            ET.SubElement(adaptive, layer, android_attributes(drawable=f"@drawable/ic_launcher_{layer}"))
        write_xml(resources / "mipmap-anydpi-v26" / f"{name}.xml", adaptive)
    for name in ("ic_launcher", "ic_launcher_round"):
        legacy = deepcopy(scene)
        legacy.set("viewBox", "18 18 72 72")
        legacy.remove(next(element for element in legacy if element.get("id") == "foreground"))
        legacy.append(deepcopy(foreground))
        clip = svg_element("clipPath", id="legacy-mask")
        if name.endswith("round"):
            clip.append(svg_element("circle", cx="54", cy="54", r="36"))
        else:
            clip.append(svg_element("rect", x="18", y="18", width="72", height="72", rx="16"))
        legacy.find(f"{{{SVG}}}defs").append(clip)
        masked = svg_element("g", **{"clip-path": "url(#legacy-mask)"})
        for element in list(legacy):
            if element.tag != f"{{{SVG}}}defs":
                legacy.remove(element)
                masked.append(element)
        legacy.append(masked)
        for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            render_png(legacy, resources / f"mipmap-{density}" / f"{name}.png", size, opaque=False)
    output.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(scene).write(output / "play-icon.svg", encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("resources", nargs="?", type=Path)
    parser.add_argument("output", nargs="?", type=Path)
    parser.add_argument("--export-png", action="store_true", help="Only export store PNGs")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "docs/store", help="Store PNG destination")
    args = parser.parse_args()
    if args.export_png and (args.resources or args.output):
        parser.error("--export-png cannot be combined with Android output paths")
    if not args.export_png and (args.resources is None or args.output is None):
        parser.error("provide Android resources and output paths, or use --export-png")
    scene = ET.parse(ROOT / "docs/design/launcher-icon/scene-b2.svg").getroot()
    definitions = {element.attrib["id"]: element for element in scene.iter() if "id" in element.attrib}
    if not args.export_png:
        generate_android(scene, definitions, args.resources, args.output)
    export_store(scene, args.output_dir)


if __name__ == "__main__":
    main()
