"""Tiny SVG toolkit used by generate.py to build the Recollect architecture diagrams.

Every colour is a CSS custom property with a dark-theme fallback, e.g. var(--surface, #1c1d21):
  * rendered INLINE on the website, the diagram inherits the site's tokens (dark/light, cobalt accent);
  * opened standalone (README, docs, browser), the fallbacks give the same dark look.
Styles are written as inline `style=` attributes (no <style> block), so nothing can leak into the page CSS.

Palette rules: neutral surfaces, cobalt for the primary path, muted grey for secondary paths,
muted red/rose ONLY for failure paths. No orange/amber.
"""
from html import escape

FONT = "Inter, system-ui, -apple-system, 'Segoe UI', sans-serif"

BG = "var(--background,#17181b)"
SURFACE = "var(--surface,#1c1d21)"
RAISED = "var(--surface-raised,#232428)"
MUTED_BG = "var(--surface-muted,#202126)"
BORDER = "var(--border,#2c2d33)"
BORDER_STRONG = "var(--border-strong,#3a3b42)"
TEXT = "var(--text-primary,#f2f2f0)"
TEXT2 = "var(--text-secondary,#a3a3a8)"
TEXT3 = "var(--text-muted,#8e8f96)"
ACCENT = "var(--accent,#4f5fff)"
ACCENT_TEXT = "var(--accent-text,#93a0ff)"
ACCENT_SUBTLE = "var(--accent-subtle,rgba(91,108,255,.16))"
ERROR = "var(--error,#f87171)"
ERROR_SUBTLE = "color-mix(in srgb, var(--error,#f87171) 10%, transparent)"

KINDS = {
    #            fill        stroke          title-colour  sub-colour  dash
    "node":    (RAISED,       BORDER_STRONG,  TEXT,         TEXT2,      None),
    "primary": (ACCENT_SUBTLE, ACCENT,         TEXT,         TEXT2,      None),
    "muted":   (SURFACE,      BORDER,         TEXT2,        TEXT3,      None),
    "fail":    (ERROR_SUBTLE, ERROR,          TEXT,         TEXT2,      "5 4"),
    "group":   ("none",       BORDER_STRONG,  TEXT3,        TEXT3,      "3 4"),
}
EDGES = {  # stroke, width, dash, marker-colour
    "primary": (ACCENT, 2.0, None),
    "secondary": (TEXT3, 1.5, None),
    "dashed": (TEXT3, 1.5, "5 4"),
    "fail": (ERROR, 1.5, "5 4"),
}


class Diagram:
    def __init__(self, uid, width, height, title, desc):
        self.uid, self.w, self.h, self.title, self.desc = uid, width, height, title, desc
        self.parts = []

    # ---------------------------------------------------------------- primitives
    def text(self, x, y, s, size=12, weight=400, fill=TEXT, anchor="start", opacity=None, tracking=None):
        extra = f' letter-spacing="{tracking}"' if tracking else ""
        op = f' opacity="{opacity}"' if opacity else ""
        self.parts.append(
            f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" font-weight="{weight}"'
            f'{extra}{op} style="fill:{fill}">{escape(s)}</text>'
        )

    def rect(self, x, y, w, h, kind="node", r=10, sw=1.25):
        fill, stroke, _, _, dash = KINDS[kind]
        d = f";stroke-dasharray:{dash}" if dash else ""
        self.parts.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" '
            f'style="fill:{fill};stroke:{stroke};stroke-width:{sw}{d}"/>'
        )

    def node(self, x, y, w, h, title, subs=(), kind="node", tag=None, r=10):
        """A service box: optional small-caps tag, bold title, muted sub-lines. Text is left-aligned."""
        self.rect(x, y, w, h, kind, r)
        _, _, tc, sc, _ = KINDS[kind]
        cy = y + 20
        if tag:
            self.text(x + 12, cy, tag.upper(), 9, 600, ACCENT_TEXT if kind == "primary" else TEXT3, tracking="0.8")
            cy += 15
        self.text(x + 12, cy, title, 13, 600, tc)
        for s in subs:
            cy += 15
            self.text(x + 12, cy, s, 10.5, 400, sc)

    def group(self, x, y, w, h, label):
        self.rect(x, y, w, h, "group", r=14, sw=1)
        self.text(x + 14, y + 20, label.upper(), 9.5, 600, TEXT3, tracking="1")

    def badge(self, x, y, n):
        self.parts.append(f'<circle cx="{x}" cy="{y}" r="10" style="fill:{ACCENT}"/>')
        self.text(x, y + 4, str(n), 11, 700, "#fff", anchor="middle")

    def edge(self, points, kind="primary", label=None, lx=None, ly=None, anchor="middle", arrow=True):
        stroke, width, dash = EDGES[kind]
        d = "M" + " L".join(f"{x},{y}" for x, y in points)
        dd = f";stroke-dasharray:{dash}" if dash else ""
        mk = f' marker-end="url(#{self.uid}-arrow-{kind})"' if arrow else ""
        self.parts.append(
            f'<path d="{d}" fill="none" style="stroke:{stroke};stroke-width:{width}{dd};'
            f'stroke-linejoin:round;stroke-linecap:round"{mk}/>'
        )
        if label:
            self.pill(lx if lx is not None else points[0][0], ly if ly is not None else points[0][1], label, anchor)

    def pill(self, x, y, s, anchor="middle", fill=None):
        """Small label with an opaque backing so lines never strike through it."""
        wpx = 5.6 * len(s) + 10
        x0 = x - wpx / 2 if anchor == "middle" else (x if anchor == "start" else x - wpx)
        self.parts.append(
            f'<rect x="{x0:.1f}" y="{y - 11}" width="{wpx:.1f}" height="16" rx="8" '
            f'style="fill:{fill or BG};stroke:{BORDER};stroke-width:1"/>'
        )
        self.text(x0 + wpx / 2, y + 0.5, s, 10, 500, TEXT2, anchor="middle")

    def note(self, x, y, lines, size=10.5, fill=TEXT3, lh=15, anchor="start", weight=400):
        for i, s in enumerate(lines):
            self.text(x, y + i * lh, s, size, weight, fill, anchor)

    def legend_swatch(self, x, y, kind, label):
        stroke, width, dash = EDGES[kind]
        dd = f";stroke-dasharray:{dash}" if dash else ""
        self.parts.append(f'<line x1="{x}" y1="{y}" x2="{x + 26}" y2="{y}" style="stroke:{stroke};stroke-width:{width}{dd}"/>')
        self.text(x + 34, y + 4, label, 10.5, 400, TEXT2)

    # ---------------------------------------------------------------- output
    def svg(self):
        defs = "".join(
            f'<marker id="{self.uid}-arrow-{k}" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" '
            f'orient="auto-start-reverse"><path d="M1,1 L9,5 L1,9 z" style="fill:{EDGES[k][0]}"/></marker>'
            for k in EDGES
        )
        return (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}" '
            f'role="img" aria-labelledby="{self.uid}-title {self.uid}-desc" font-family="{FONT}">\n'
            f'<title id="{self.uid}-title">{escape(self.title)}</title>\n'
            f'<desc id="{self.uid}-desc">{escape(self.desc)}</desc>\n'
            f'<defs>{defs}</defs>\n'
            f'<rect width="{self.w}" height="{self.h}" rx="16" style="fill:{BG}"/>\n'
            + "\n".join(self.parts)
            + "\n</svg>\n"
        )
