"""Samples Google's own dark palette out of screenshots of Google apps.

Put dark-mode screenshots next to this script, named as in SHOTS below, then run:

    python sample-google-dark-palette.py

The values it reports are what CastColor.dark() and app/res/values/colors.xml use. The
point is that they are measured rather than guessed: the M3 *baseline* dark palette
(#D0BCFF on #141218, purple-tinted) is what the spec's reference theme uses, and no
Google app actually ships it.

Where each role came from, for the five apps that were sampled:

    surface              #131313   largest flat area in Play Store, Earth and Maps
    primary              #B2C5FF   Gboard's "Add keyboard", Translate's mic FAB
    onPrimary            #002E69   the dark glyph on those two
    secondaryContainer   #004A77   Google Earth's filled buttons and info banner
    onSecondaryContainer #C2E7FF   the light text on them
    outline              #8E918F   Maps' chip family (those chips fill at #393939)
"""
from PIL import Image
import collections
import os
import sys

# name -> filename of a screenshot of that app in dark mode
SHOTS = {
    "PlayStore": "play-store.png",
    "Translate": "translate.png",
    "Maps": "maps.png",
    "Earth": "google-earth.png",
    "Gboard": "gboard.png",
}


def hx(p):
    return "#%02X%02X%02X" % p[:3]


def load():
    ims = {}
    for name, path in SHOTS.items():
        if os.path.exists(path):
            ims[name] = Image.open(path).convert("RGB")
        else:
            print("missing (skipped): %s -> %s" % (name, path), file=sys.stderr)
    if not ims:
        print("no screenshots found; put them next to this script", file=sys.stderr)
        sys.exit(1)
    return ims


def dominant(im, label, box=None):
    """Most common colour overall, or inside a box."""
    crop = im.crop(box) if box else im
    if crop.width > 400:
        crop = crop.resize((crop.width // 8, crop.height // 8), Image.NEAREST)
    counts = collections.Counter(crop.getdata())
    total = crop.width * crop.height
    print("  %-24s %s" % (label, "  ".join(
        "%s %.0f%%" % (hx(c), 100.0 * n / total) for c, n in counts.most_common(4))))


def extreme(im, label, box, darkest=True):
    """Darkest or brightest pixel in a box — recovers on-colours from glyph cores."""
    data = list(im.crop(box).getdata())
    px = min(data, key=sum) if darkest else max(data, key=sum)
    print("  %-24s %s" % (label, hx(px)))


def main():
    ims = load()

    print("=== surfaces: the largest flat area is the background ===")
    for name, im in ims.items():
        dominant(im, name)

    # Boxes are in source pixels, so they differ per screenshot and per device. These are
    # the ones that worked on a 1440-wide Pixel capture; adjust for your own.
    if "Gboard" in ims:
        print("\n=== Gboard: a filled button and the dark glyph on it ===")
        dominant(ims["Gboard"], "Add keyboard fill", (150, 1450, 700, 1545))
        extreme(ims["Gboard"], "its glyph", (150, 1450, 700, 1545), darkest=True)
    if "Earth" in ims:
        print("\n=== Google Earth: the container-blue button ===")
        dominant(ims["Earth"], "Explore Earth fill", (400, 2500, 1050, 2580))
        extreme(ims["Earth"], "its light text", (400, 2500, 1050, 2580), darkest=False)
    if "Translate" in ims:
        print("\n=== Translate: the accent FAB ===")
        dominant(ims["Translate"], "mic FAB fill", (1150, 1570, 1280, 1700))
        extreme(ims["Translate"], "its icon", (1150, 1570, 1280, 1700), darkest=True)


if __name__ == "__main__":
    main()
