from PIL import Image, ImageDraw, ImageFont
import math, os

FD = r"C:/Users/Prakyath tej/.claude/skills/synced/e718a836-0791-4b9f-a1f0-65aad5825c4a_5163a3fd-9ab0-46d2-8f86-ef945ad97ebc/canvas-design/canvas-fonts"
def F(n, s): return ImageFont.truetype(os.path.join(FD, n), s)
mono = lambda s: F("GeistMono-Regular.ttf", s)
monob = lambda s: F("GeistMono-Bold.ttf", s)
disp = lambda s: F("BigShoulders-Bold.ttf", s)
serif = lambda s: F("InstrumentSerif-Italic.ttf", s)

W, H, M = 3600, 2400, 130
INK, INK2 = (14, 17, 22), (22, 26, 33)
BONE, DIM, FAINT = (228, 222, 208), (120, 122, 124), (48, 53, 61)
RED, COOL = (240, 78, 44), (120, 176, 196)

img = Image.new("RGB", (W, H), INK)
d = ImageDraw.Draw(img)
def T(xy, s, f, c, a="la"): d.text(xy, s, font=f, fill=c, anchor=a)
def dashed(x0, y0, x1, y1, c, w=2, seg=12, gap=10):
    L = math.hypot(x1 - x0, y1 - y0); ux, uy = (x1 - x0) / L, (y1 - y0) / L; t = 0
    while t < L:
        e = min(t + seg, L)
        d.line([x0 + ux * t, y0 + uy * t, x0 + ux * e, y0 + uy * e], fill=c, width=w); t += seg + gap
def circ(x, y, r, c, dot=True, w=3):
    d.ellipse([x - r, y - r, x + r, y + r], outline=c, width=w, fill=INK)
    if dot: d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=c)

# grid + frame
for gx in range(M, W - M + 1, 60):
    for gy in range(M, H - M + 1, 60):
        d.point((gx, gy), fill=FAINT)
d.rectangle([M - 40, M - 40, W - M + 40, H - M + 40], outline=FAINT, width=1)
for cx, cy in [(M - 40, M - 40), (W - M + 40, M - 40), (M - 40, H - M + 40), (W - M + 40, H - M + 40)]:
    d.line([cx - 18, cy, cx + 18, cy], fill=BONE, width=2); d.line([cx, cy - 18, cx, cy + 18], fill=BONE, width=2)

# header
T((M, M + 10), "THE BRAIN", disp(120), BONE)
T((M + 4, M + 150), "FIELD SURVEY 02  ·  CAPTUREOS  ·  PLANNER / WORKER / EXECUTOR", mono(24), DIM)
T((W - M, M + 26), "RUNS NOW ON PC", mono(24), BONE, "ra")
T((W - M, M + 62), "FASTAPI  ·  OLLAMA GEMMA  ·  CLOUD API", mono(24), DIM, "ra")
T((W - M, M + 98), "SAME PATH MOVES TO PHONE", mono(24), DIM, "ra")
T((W - M, M + 150), "CLOUD SPLITS  ·  BOTH SOLVE  ·  GEMMA ACTS", mono(24), RED, "ra")

T((M, 420), "A · FLOW", monob(24), BONE)
T((M + 1520, 420), "CLOUD", mono(20), COOL); dashed(M + 1620, 432, M + 1700, 432, COOL); T((M + 1716, 420), "network", mono(20), DIM)
T((M + 1900, 420), "LOCAL", mono(20), RED); d.line([M + 1990, 432, M + 2070, 432], fill=RED, width=3); T((M + 2086, 420), "on-device", mono(20), DIM)

cy = 920
UP, LO = 700, 1140
XIN, XPL, XL0, XL1, XMG, XEX, XOUT = 330, 900, 1420, 2240, 2520, 2880, 3230
lane_x = [1540, 1760, 1980, 2200]

def stage(x, n): T((x, 560), n, mono(18), DIM, "ma")
for x, n in [(XIN, "01"), (XPL, "02"), ((XL0 + XL1) // 2, "03"), (XMG, "04"), (XEX, "05"), (XOUT + 60, "06")]:
    stage(x, n)
    d.line([x, 590, x, 604], fill=DIM, width=1)

# 01 input
circ(XIN, cy, 46, BONE)
T((XIN, cy + 80), "VOICE", monob(22), BONE, "ma")
T((XIN, cy + 112), "→ transcript", mono(19), DIM, "ma")
d.line([XIN + 60, cy, XPL - 86, cy], fill=BONE, width=2)

# 02 planner diamond
r = 72
d.polygon([(XPL, cy - r), (XPL + r, cy), (XPL, cy + r), (XPL - r, cy)], outline=COOL, width=3, fill=INK)
d.polygon([(XPL, cy - 26), (XPL + 26, cy), (XPL, cy + 26), (XPL - 26, cy)], fill=COOL)
T((XPL, cy + 104), "CLOUD PLANNER", monob(22), COOL, "ma")
T((XPL, cy + 136), "split · tag hard / easy", mono(19), DIM, "ma")

# 03 lanes
d.rectangle([XL0 - 20, UP - 90, XL1 + 20, UP + 90], outline=FAINT, width=1, fill=INK2)
d.rectangle([XL0 - 20, LO - 90, XL1 + 20, LO + 90], outline=FAINT, width=1, fill=INK2)
T((XL0, UP - 78), "HARD  →  CLOUD", monob(20), COOL)
T((XL0, LO - 78), "EASY  →  GEMMA LOCAL", monob(20), RED)
dashed(XPL + r + 8, cy - 8, XL0 - 20, UP, COOL)
d.line([XPL + r + 8, cy + 8, XL0 - 20, LO], fill=RED, width=3)
dashed(XL0 - 20, UP, XL1 + 20, UP, COOL)
d.line([XL0 - 20, LO, XL1 + 20, LO], fill=RED, width=3)
for x, lab in zip(lane_x, ["SUMMARY", "DECISIONS", "OWNERS", "REPORT"]):
    circ(x, UP, 12, COOL, dot=False, w=3)
    T((x, UP + 30), lab, mono(19), BONE, "ma")
for x, lab in zip(lane_x, ["DATES", "PRIORITY", "ENTITIES", "FORMAT"]):
    d.ellipse([x - 12, LO - 12, x + 12, LO + 12], fill=RED)
    T((x, LO + 30), lab, mono(19), BONE, "ma")
# parallel marker
px = (XL0 + XL1) // 2
for dx in (-8, 8):
    d.line([px + dx, UP + 110, px + dx, LO - 110], fill=BONE, width=2)
T((px + 30, cy - 12), "PARALLEL", monob(20), BONE)
T((px + 30, cy + 18), "asyncio.gather", mono(18), DIM)

# 04 merge
dashed(XL1 + 20, UP, XMG - 30, cy - 18, COOL)
d.line([XL1 + 20, LO, XMG - 30, cy + 18], fill=RED, width=3)
circ(XMG, cy, 32, BONE)
T((XMG, cy + 64), "MERGE", monob(22), BONE, "ma")
T((XMG, cy + 96), "one action plan", mono(19), DIM, "ma")
d.line([XMG + 40, cy, XEX - 80, cy], fill=BONE, width=2)

# 05 executor
circ(XEX, cy, 66, RED, w=4)
d.ellipse([XEX - 86, cy - 86, XEX + 86, cy + 86], outline=RED, width=1)
T((XEX, cy + 112), "GEMMA EXECUTOR", monob(22), RED, "ma")
T((XEX, cy + 144), "validate · fill · tool calls", mono(19), DIM, "ma")
T((XEX, cy + 172), "code runs them", mono(19), DIM, "ma")

# 06 outputs
outs = [(cy - 130, "NOTION TASK"), (cy, "NOTION NOTE"), (cy + 130, "ASK USER")]
for y, lab in outs:
    d.line([XEX + 92, cy + (y - cy) * 0.25, XOUT - 18, y], fill=BONE, width=2)
    d.rectangle([XOUT - 16, y - 16, XOUT + 16, y + 16], outline=BONE, width=3, fill=INK)
    T((XOUT + 32, y), lab, mono(20), BONE, "lm")

# divider
d.line([M, 1440, W - M, 1440], fill=FAINT, width=1)

# B · time strip
T((M, 1490), "B · ONE REQUEST, SECONDS", monob(24), BONE)
T((M, 1526), "illustrative · measure real numbers on the day", mono(18), DIM)
X0, SC = 520, 165
rows = [
    ("SPEECH", 0.0, 1.8, BONE, False),
    ("CLOUD PLAN", 1.8, 3.0, COOL, True),
    ("CLOUD · HARD", 3.0, 7.0, COOL, True),
    ("GEMMA · EASY", 3.0, 5.6, RED, False),
    ("MERGE", 7.0, 7.3, BONE, False),
    ("GEMMA · EXECUTE", 7.3, 8.6, RED, False),
    ("NOTION WRITE", 8.6, 9.6, BONE, False),
]
ry0 = 1600
for i, (lab, a, b, c, dash) in enumerate(rows):
    y = ry0 + i * 62
    T((M, y), lab, mono(20), DIM if c == BONE else c, "lm")
    xa, xb = X0 + a * SC, X0 + b * SC
    if dash:
        d.rectangle([xa, y - 12, xb, y + 12], outline=c, width=2)
        for hx in range(int(xa) + 10, int(xb), 16):
            d.line([hx, y - 12, hx - 12, y + 12], fill=c, width=1)
    else:
        d.rectangle([xa, y - 12, xb, y + 12], fill=c)
ay = ry0 + len(rows) * 62 - 10
d.line([X0, ay, X0 + 10 * SC, ay], fill=DIM, width=1)
for s_ in range(11):
    x = X0 + s_ * SC
    d.line([x, ay, x, ay + 12], fill=DIM, width=1)
    T((x, ay + 22), f"{s_}s", mono(17), DIM, "ma")
# parallel bracket
bx = X0 + 3.0 * SC
d.line([bx, ry0 + 2 * 62 - 30, bx, ry0 + 3 * 62 + 30], fill=BONE, width=1)
T((X0 + 5.8 * SC, ry0 + 3 * 62), "← overlap = saved time", mono(18), BONE, "lm")

# C · failsafes
cx = 2330
T((cx, 1490), "C · FAILSAFES", monob(24), BONE)
fs = [
    ("NO NETWORK", "GEMMA RUNS WHOLE PLAN"),
    ("CLOUD > 8s", "USE GEMMA RESULT"),
    ("PRIVATE MODE", "LOCAL ONLY"),
    ("BEFORE CLOUD", "REDACT NAMES · NUMBERS"),
    ("BAD JSON", "REPAIR · RETRY · SAVE NOTE"),
    ("NOTION DOWN", "QUEUE · FLUSH LATER"),
]
for i, (p, f) in enumerate(fs):
    y = 1590 + i * 66
    T((cx, y), f"{i + 1:02d}", mono(21), RED)
    T((cx + 60, y), p, mono(21), DIM)
    fw = d.textlength(f, font=mono(21)); fx = W - M - fw
    d.line([cx + 60 + d.textlength(p, font=mono(21)) + 20, y + 13, fx - 20, y + 13], fill=FAINT, width=1)
    T((fx, y), f, mono(21), BONE)

# footer
T((M, H - M - 10), "the cloud thinks, the phone acts", serif(44), BONE, "ls")
T((W - M, H - M - 12), "SHEET 02 / 02  ·  TRANSCRIPT → PLAN → PARALLEL → EXECUTE", mono(20), DIM, "rs")

img.save(os.path.join(os.path.dirname(__file__), "CaptureOS_Brain.png"))
print("ok")
