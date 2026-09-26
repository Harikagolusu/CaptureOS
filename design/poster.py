from PIL import Image, ImageDraw, ImageFont
import os

FD = r"C:/Users/Prakyath tej/.claude/skills/synced/e718a836-0791-4b9f-a1f0-65aad5825c4a_5163a3fd-9ab0-46d2-8f86-ef945ad97ebc/canvas-design/canvas-fonts"
def F(name, size): return ImageFont.truetype(os.path.join(FD, name), size)

W, H = 3600, 2400
M = 130
INK = (14, 17, 22)
INK2 = (22, 26, 33)
BONE = (228, 222, 208)
DIM = (120, 122, 124)
FAINT = (48, 53, 61)
RED = (240, 78, 44)
COOL = (120, 176, 196)

img = Image.new("RGB", (W, H), INK)
d = ImageDraw.Draw(img)

mono = lambda s: F("GeistMono-Regular.ttf", s)
monob = lambda s: F("GeistMono-Bold.ttf", s)
disp = lambda s: F("BigShoulders-Bold.ttf", s)
serif = lambda s: F("InstrumentSerif-Italic.ttf", s)

def text(xy, s, font, fill, anchor="la"):
    d.text(xy, s, font=font, fill=fill, anchor=anchor)

# dotted survey grid
for gx in range(M, W - M + 1, 60):
    for gy in range(M, H - M + 1, 60):
        d.point((gx, gy), fill=FAINT)

# frame + corner registration marks
d.rectangle([M - 40, M - 40, W - M + 40, H - M + 40], outline=FAINT, width=1)
for cx, cy in [(M - 40, M - 40), (W - M + 40, M - 40), (M - 40, H - M + 40), (W - M + 40, H - M + 40)]:
    d.line([cx - 18, cy, cx + 18, cy], fill=BONE, width=2)
    d.line([cx, cy - 18, cx, cy + 18], fill=BONE, width=2)

# ── header
text((M, M + 10), "CAPTUREOS", disp(120), BONE)
text((M + 4, M + 150), "FIELD SURVEY 01  ·  BATTLE PLAN  ·  TEAM BUILDX  ·  3 OPERATORS  ·  FLUTTER", mono(24), DIM)
text((W - M, M + 26), "iQOO CITY BATTLES", mono(24), BONE, "ra")
text((W - M, M + 62), "HYDERABAD  ·  PRODUCTIVITY", mono(24), DIM, "ra")
text((W - M, M + 98), "17.3850° N  78.4867° E", mono(24), DIM, "ra")
text((W - M, M + 150), "LOCAL GEMMA FOR SMALL · CLOUD FOR HEAVY", mono(24), RED, "ra")

# ── timeline geometry
X0, X1 = M + 250, W - M - 40
MER = 2180
LANES = [("P1", "APP", 700), ("P2", "ON-DEVICE AI", 900), ("P3", "LAPTOP · PITCH", 1100)]
AXIS = 1250
PRE = [480, 900, 1320, 1760]
POST = [2440, 2860]
GL = 3300

# regions
d.rectangle([MER, 580, W - M, AXIS + 90], fill=INK2)
d.rectangle([GL - 150, 580, W - M, AXIS + 90], fill=(20, 32, 38))

# meridian
d.line([MER, 380, MER, AXIS + 90], fill=RED, width=4)
text((MER + 36, 300), "13:00", disp(210), RED)
text((MER + 44, 580 + 22), "PC LOCKED  ·  PHONE ONLY", mono(22), RED)
text((MER - 30, 580 + 22), "APK + MODELS ON ALL 3 PHONES", mono(22), BONE, "ra")

# lanes
milestones = {
    "P1": ["FLUTTER SKELETON · APK", "CAPTURE · REVIEW · QUEUE", "LAB · MOCK · PHONE PDF", "INSTALL ×3"],
    "P2": ["GEMMA 3n ON iQOO", "SPEECH te·hi·en → JSON", "HYBRID ROUTER · JSON REPAIR", "MODELS PUSHED"],
    "P3": ["FASTAPI · CLOUD PROXY", "DASHBOARD · SLACK · DOCX", "HOTSPOT SYNC TEST", "SERVER FROZEN"],
}
post = {
    "P1": ["REHEARSE FIELD ×5", "BACKUP VIDEO"],
    "P2": ["ROUTER TUNE IN LAB", "TELUGU LIVE TEST"],
    "P3": ["PITCH · JUDGE Q&A", "DEMO SCRIPT"],
}
for code, name, y in LANES:
    text((M, y - 30), code, monob(30), BONE)
    text((M, y + 10), name, mono(20), DIM)
    # pre line (solid) and post line (dashed)
    d.line([PRE[0], y, MER, y], fill=BONE, width=2)
    for x in range(MER, GL - 150, 22):
        d.line([x, y, min(x + 11, GL - 150), y], fill=COOL, width=2)
    for i, x in enumerate(PRE):
        d.ellipse([x - 11, y - 11, x + 11, y + 11], fill=BONE)
        text((x, y - 26), milestones[code][i], mono(21), BONE, "md")
    for i, x in enumerate(POST):
        d.ellipse([x - 11, y - 11, x + 11, y + 11], outline=COOL, width=3, fill=INK2)
        text((x, y - 26), post[code][i], mono(21), COOL, "md")
    # converge to green light
    import math
    a = math.atan2(900 - y, 150); d.line([GL - 150, y, GL - 50 * math.cos(a), 900 - 50 * math.sin(a)], fill=COOL, width=2)

d.ellipse([GL - 26, 900 - 26, GL + 26, 900 + 26], fill=RED)
d.ellipse([GL - 44, 900 - 44, GL + 44, 900 + 44], outline=RED, width=2)
text((GL - 130, 614), "GREEN LIGHT", monob(22), BONE)
text((GL - 130, 650), "SYNC · SLACK · DOCX", mono(18), COOL)
text((GL - 130, 1150), "→ LIVE DEMO", mono(18), RED)

# axis
d.line([X0 - 20, AXIS, W - M, AXIS], fill=DIM, width=2)
for x in range(X0, W - M, 30):
    d.line([x, AXIS, x, AXIS + (14 if (x - X0) % 150 == 0 else 7)], fill=DIM, width=1)
for x, lab in zip(PRE, ["K+0:00 SPIKES", "K+0:45 BUILD", "K+2:30 INTEGRATE", "12:15 FREEZE"]):
    d.line([x, AXIS - 10, x, AXIS + 24], fill=BONE, width=2)
    text((x, AXIS + 34), lab, mono(21), BONE, "ma")
for x, lab in zip(POST, ["TUNE", "REHEARSE"]):
    d.line([x, AXIS - 10, x, AXIS + 24], fill=COOL, width=2)
    text((x, AXIS + 34), lab, mono(21), COOL, "ma")
text((X0 - 20, AXIS - 40), "PC · BUILD WINDOW", mono(20), DIM)
text((MER + 30, AXIS - 40), "RED LIGHT", mono(20), RED)
text((W - M - 20, AXIS - 40), "LAPTOP BACK", mono(20), COOL, "ra")
text((PRE[0], AXIS + 74), "CHECKPOINT · GEMMA RUNS ON PHONE? ELSE GEMMA 3 1B TEXT-ONLY", mono(18), RED)

# divider
d.line([M, 1450, W - M, 1450], fill=FAINT, width=1)
text((M, 1490), "A · SIGNAL PATH", monob(24), BONE)
text((2230, 1490), "B · WEAK POINTS → COUNTERMEASURES", monob(24), BONE)

# ── architecture chain (bottom-left): hybrid router
cy = 1790
def node(x, y, r, col, fill_dot=True):
    d.ellipse([x - r, y - r, x + r, y + r], outline=col, width=3)
    if fill_dot:
        d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=col)
def dashed(x0, y0, x1, y1, col, seg=11, gap=11):
    import math
    L = math.hypot(x1 - x0, y1 - y0); ux, uy = (x1 - x0) / L, (y1 - y0) / L
    t = 0
    while t < L:
        e = min(t + seg, L)
        d.line([x0 + ux * t, y0 + uy * t, x0 + ux * e, y0 + uy * e], fill=col, width=2)
        t += seg + gap

XS = {"cap": 280, "asr": 620, "rt": 960, "br": 1300, "rev": 1640, "q": 1980}
LY, CYC = 1650, 1920
# 01 capture, 02 speech
for key, n, a_, b_ in [("cap", "01", "MIC · CAMERA", "record · image_picker"), ("asr", "02", "SPEECH", "on-device te · hi · en")]:
    node(XS[key], cy, 42, BONE)
    text((XS[key], cy + 84), a_, monob(22), BONE, "ma")
    text((XS[key], cy + 118), b_, mono(19), DIM, "ma")
    text((XS[key], cy - 68), n, mono(18), DIM, "md")
d.line([XS["cap"] + 56, cy, XS["asr"] - 56, cy], fill=BONE, width=2)
d.line([XS["asr"] + 56, cy, XS["rt"] - 60, cy], fill=BONE, width=2)
# 03 router diamond
rx, rr = XS["rt"], 46
d.polygon([(rx, cy - rr), (rx + rr, cy), (rx, cy + rr), (rx - rr, cy)], outline=RED, width=3)
d.ellipse([rx - 7, cy - 7, rx + 7, cy + 7], fill=RED)
text((rx, cy - 70), "03", mono(18), DIM, "md")
text((rx, cy + 84), "ROUTER", monob(22), RED, "ma")
text((rx, cy + 118), "size · signal · privacy", mono(19), DIM, "ma")
# branches
bx = XS["br"]
d.line([rx + rr + 10, cy - 10, bx - 60, LY + 20], fill=RED, width=3)
dashed(rx + rr + 10, cy + 10, bx - 56, CYC - 20, COOL)
node(bx, LY, 46, RED)
text((bx, LY - 118), "LOCAL · GEMMA 3n", monob(22), RED, "ma")
text((bx, LY - 84), "phone GPU · default", mono(19), DIM, "ma")
text((bx, cy - 42), "↑ short · private · offline", mono(18), BONE, "ma")
node(bx, CYC, 42, COOL)
text((bx, cy + 18), "↓ long · report · unsure", mono(18), BONE, "ma")
text((bx, CYC + 58), "CLOUD LLM", monob(22), COOL, "ma")
text((bx, CYC + 88), "via proxy · redacted text", mono(19), DIM, "ma")
# merge into review
rvx = XS["rev"]
d.line([bx + 60, LY + 20, rvx - 56, cy - 10], fill=RED, width=3)
dashed(bx + 56, CYC - 20, rvx - 56, cy + 10, COOL)
for key, n, a_, b_ in [("rev", "04", "REVIEW", "human approves"), ("q", "05", "QUEUE", "sqflite · phone PDF")]:
    node(XS[key], cy, 42, BONE)
    text((XS[key], cy + 84), a_, monob(22), BONE, "ma")
    text((XS[key], cy + 118), b_, mono(19), DIM, "ma")
    text((XS[key], cy - 68), n, mono(18), DIM, "md")
d.line([XS["rev"] + 56, cy, XS["q"] - 56, cy], fill=BONE, width=2)
text((M, 1580), "LOCAL-FIRST  ·  CLOUD WHEN IT MATTERS", mono(20), RED)
text((M, 1612), "NO SIGNAL / PRIVATE MODE → ALWAYS LOCAL", mono(18), DIM)

# sync hop to laptop
sx = XS["q"]
d.line([sx, cy + 150, sx, 2100], fill=COOL, width=2)
for x in range(sx, 1400, 20):
    pass
lx = [XS["cap"], (XS["cap"] + XS["rev"]) / 2 + 60, XS["rev"] + 40]
for x in range(int(lx[0]), sx, 22):
    d.line([x, 2100, min(x + 11, sx), 2100], fill=COOL, width=2)
text((sx + 20, 2010), "HOTSPOT SYNC", mono(19), COOL)
for x, (a, b) in zip(lx, [("SLACK / NOTION", "meeting → tasks"), ("LIVE DASHBOARD", "fastapi · websocket"), ("DOCX REPORT", "field → document")]):
    x = int(x)
    d.rectangle([x - 16, 2100 - 16, x + 16, 2100 + 16], outline=COOL, width=3, fill=INK)
    text((x, 2140), a, monob(21), COOL, "ma")
    text((x, 2172), b, mono(19), DIM, "ma")

# ── weak points (bottom-right)
wp = [
    ("OTTER EXISTS", "FIELD MODE = HERO"),
    ("TELUGU ASR", "ON-DEVICE te-IN + GEMMA"),
    ("NPU CLAIM", "GPU · SAY IT HONEST"),
    ("SLOW VISION", "BACKGROUND QUEUE"),
    ("BROKEN JSON", "REPAIR · RETRY · FALLBACK"),
    ("CLOUD vs PRIVACY", "PRIVATE MODE · REDACT FIRST"),
    ("NO PC AFTER 1", "IN-APP LAB SCREEN"),
    ("VENUE WI-FI", "OWN HOTSPOT + QUEUE"),
    ("LIVE DEMO FAIL", "MOCK MODE + VIDEO"),
]
wx, wy = 2230, 1570
for i, (p, f) in enumerate(wp):
    y = wy + i * 64
    text((wx, y), f"{i + 1:02d}", mono(21), RED)
    text((wx + 60, y), p, mono(21), DIM)
    tw = d.textlength(f, font=mono(21))
    fx = W - M - tw
    d.line([wx + 60 + d.textlength(p, font=mono(21)) + 20, y + 13, fx - 20, y + 13], fill=FAINT, width=1)
    text((fx, y), f, mono(21), BONE)

# footer
text((M, H - M - 10), "your phone becomes the eyes, ears, and hands of your workday", serif(40), BONE, "ls")
text((W - M, H - M - 12), "SHEET 01 / 01  ·  KICKOFF → 13:00 → GREEN LIGHT", mono(20), DIM, "rs")

img.save(os.path.join(os.path.dirname(__file__), "CaptureOS_Battle_Plan.png"))
print("ok")
