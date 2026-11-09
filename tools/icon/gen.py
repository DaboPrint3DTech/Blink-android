"""Genera l'icona dell'app (SVG di anteprima + vector drawable Android) da un'unica descrizione.

Uso: python3 tools/icon/gen.py <cartella_anteprime> app/src/main/res
"""
import sys, os

OUT = sys.argv[1]
RES = sys.argv[2] if len(sys.argv) > 2 else None

INK = "#1D2B2F"       # contorno scuro
TEAL = "#2DB5A9"      # turchese DaboPrint3D
TEAL_DARK = "#1E8F86"
ORANGE = "#F7892B"
ORANGE_DARK = "#D9650F"
ORANGE_LIGHT = "#FFB066"
CUFF = "#3A3F42"
CUFF_LIGHT = "#5B6266"
WHITE = "#FFFFFF"

def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"

def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0,1 {r},{r} v{h - 2 * r} a{r},{r} 0 0,1 {-r},{r} "
            f"h{-(w - 2 * r)} a{r},{r} 0 0,1 {-r},{-r} v{-(h - 2 * r)} a{r},{r} 0 0,1 {r},{-r} Z")

# ---------------------------------------------------------------- "b" (coordinate 108x108)
STEM = rrect(31.5, 30, 11.5, 45, 5.75)
BOWL = circle(49, 62, 14.5)
COUNTER = circle(49, 62, 5.6)

def b_layers():
    return [
        # contorno unito (stessa forma ingrandita dal tratto)
        dict(d=STEM, fill=INK, stroke=INK, sw=5),
        dict(d=BOWL, fill=INK, stroke=INK, sw=5),
        # riempimento
        dict(d=STEM, fill=TEAL),
        dict(d=BOWL, fill=TEAL),
        # ombra interna in basso per effetto "gonfio"
        dict(d="M40,69.5 A10.8,10.8 0 0,0 58,69.5", fill="none", stroke=TEAL_DARK, sw=2.4, cap="round"),
        # occhiello
        dict(d=COUNTER, fill=WHITE, stroke=INK, sw=2.6),
        # riflessi
        dict(d="M35.5,36 v20", fill="none", stroke=WHITE, sw=2.6, cap="round", alpha=0.75),
        dict(d="M42,52.5 a12,12 0 0,1 9,-3.5", fill="none", stroke=WHITE, sw=2.2, cap="round", alpha=0.7),
    ]

# ---------------------------------------------------------------- mano (coordinate locali 0..100)
CUFF_D = "M10,56 L27,49 L36,90 L19,97 Z"
PALM_D = "M26,52 C26,43 33,38 42,38 L64,39 L66,90 L42,93 C33,93 28,87 28,79 Z"
THUMB_D = "M31,45 C26,31 30,13 39,5 C44,0 57,1 57,11 C57,21 51,29 53,42 Z"
FINGERS = [rrect(49, 37, 39, 13, 6.5), rrect(49, 50, 37, 13, 6.5), rrect(48, 63, 34, 13, 6.5), rrect(46, 76, 29, 13, 6.5)]

def hand_layers():
    L = [
        dict(d=CUFF_D, fill=CUFF, stroke=INK, sw=4, join="round"),
        dict(d="M14,60 L30,53", fill="none", stroke=CUFF_LIGHT, sw=3, cap="round"),
        dict(d=PALM_D, fill=ORANGE, stroke=INK, sw=4, join="round"),
        dict(d="M34,80 C40,86 52,88 60,86", fill="none", stroke=ORANGE_DARK, sw=3, cap="round"),
        dict(d=THUMB_D, fill=ORANGE, stroke=INK, sw=4, join="round"),
        dict(d="M41,12 C38,18 36,26 37,34", fill="none", stroke=ORANGE_LIGHT, sw=3.4, cap="round"),
    ]
    for f in FINGERS:
        L.append(dict(d=f, fill=ORANGE, stroke=INK, sw=4, join="round"))
    # riflessi sulle dita
    for y, x2 in [(41, 80), (54, 78), (67, 74), (80, 67)]:
        L.append(dict(d=f"M56,{y} H{x2}", fill="none", stroke=ORANGE_LIGHT, sw=2.2, cap="round", alpha=0.9))
    # piega pollice
    L.append(dict(d="M44,40 C47,44 50,45 52,44", fill="none", stroke=INK, sw=2.5, cap="round"))
    return L

HAND = dict(tx=48, ty=23, s=0.40)

# ---------------------------------------------------------------- sfondo
def background_layers():
    return [
        dict(d="M0,0 H108 V108 H0 Z", fill=WHITE),
        dict(d=circle(54, 54, 33.6), fill="none", stroke=TEAL, sw=2.6),
    ]

def svg_path(l):
    a = [f'd="{l["d"]}"', f'fill="{l["fill"]}"']
    if l.get("stroke"):
        a += [f'stroke="{l["stroke"]}"', f'stroke-width="{l["sw"]}"',
              f'stroke-linejoin="{l.get("join", "round")}"', f'stroke-linecap="{l.get("cap", "butt")}"']
    if "alpha" in l:
        a.append(f'opacity="{l["alpha"]}"')
    return "<path " + " ".join(a) + "/>"

def xml_path(l, indent="    "):
    a = [f'android:pathData="{l["d"]}"']
    a.append(f'android:fillColor="{"#00000000" if l["fill"] == "none" else l["fill"]}"')
    if l.get("stroke"):
        a += [f'android:strokeColor="{l["stroke"]}"', f'android:strokeWidth="{l["sw"]}"',
              f'android:strokeLineJoin="{l.get("join", "round")}"', f'android:strokeLineCap="{l.get("cap", "butt")}"']
    if "alpha" in l:
        a += [f'android:fillAlpha="{l["alpha"]}"', f'android:strokeAlpha="{l["alpha"]}"']
    if l.get("evenodd"):
        a.append('android:fillType="evenOdd"')
    sep = "\n" + indent + "    "
    return f"{indent}<path{sep}" + sep.join(a) + " />"

def svg_doc(bg, fg, size=432, mask="none"):
    clip = ""
    if mask == "circle":
        clip = '<clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath>'
    elif mask == "squircle":
        clip = '<clipPath id="m"><rect x="18" y="18" width="72" height="72" rx="22"/></clipPath>'
    g_open = '<g clip-path="url(#m)">' if mask != "none" else "<g>"
    hand = f'<g transform="translate({HAND["tx"]} {HAND["ty"]}) scale({HAND["s"]})">' + "".join(svg_path(l) for l in hand_layers()) + "</g>"
    view = "18 18 72 72" if mask != "none" else "0 0 108 108"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">{clip}{g_open}'
            + "".join(svg_path(l) for l in bg) + "".join(svg_path(l) for l in fg) + hand + "</g></svg>")

os.makedirs(OUT, exist_ok=True)
for mask in ["none", "circle", "squircle"]:
    open(f"{OUT}/icon-{mask}.svg", "w").write(svg_doc(background_layers(), b_layers(), mask=mask))
# solo logo, sfondo trasparente (per la schermata di login)
open(f"{OUT}/logo.svg", "w").write(svg_doc([], b_layers()))

if RES:
    hdr = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp"\n    android:height="108dp"\n'
           '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')
    hand_group = (f'    <group\n        android:translateX="{HAND["tx"]}"\n        android:translateY="{HAND["ty"]}"\n'
                  f'        android:scaleX="{HAND["s"]}"\n        android:scaleY="{HAND["s"]}">\n'
                  + "\n".join(xml_path(l, "        ") for l in hand_layers()) + "\n    </group>\n")
    fg = (hdr + "    <!-- Icona generata da tools/icon/gen.py: \"b\" turchese + mano col guanto arancione (DaboPrint3D) -->\n"
          + "\n".join(xml_path(l) for l in b_layers()) + "\n" + hand_group + "</vector>\n")
    open(f"{RES}/drawable/ic_launcher_foreground.xml", "w").write(fg)
    bg = hdr + "\n".join(xml_path(l) for l in background_layers()) + "\n</vector>\n"
    open(f"{RES}/drawable/ic_launcher_background.xml", "w").write(bg)
    # monocromatica (icone a tema): "b" con occhiello vuoto (evenOdd) + sagoma della mano
    mono_hand = [dict(d=x["d"], fill="#000000", stroke="#000000", sw=4) for x in hand_layers() if x["fill"] != "none"]
    mono = (hdr
            # asta più stretta a destra così non invade l'occhiello (non si può "cancellare" in un vector)
            + xml_path(dict(d=rrect(29, 27.5, 14, 50, 7), fill="#000000")) + "\n"
            + xml_path(dict(d=circle(49, 62, 17) + " " + COUNTER, fill="#000000", evenodd=True)) + "\n"
            + f'    <group\n        android:translateX="{HAND["tx"]}"\n        android:translateY="{HAND["ty"]}"\n'
              f'        android:scaleX="{HAND["s"]}"\n        android:scaleY="{HAND["s"]}">\n'
            + "\n".join(xml_path(l, "        ") for l in mono_hand) + "\n    </group>\n</vector>\n")
    open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w").write(mono)
print("ok")
