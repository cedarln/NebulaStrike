#!/usr/bin/env python3
"""Generates the NebulaStrike launcher icons as PNGs without external deps."""
import zlib, struct, math, os

def png(w, h, pixels):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes(row) for row in pixels)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))

def icon(size):
    px = []
    cx = cy = (size - 1) / 2
    r_bg = size * 0.48
    for y in range(size):
        row = []
        for x in range(size):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            nx, ny = dx / size, dy / size
            # background: dark space circle w/ subtle vertical gradient
            if d <= r_bg:
                t = (y / size)
                r_ = int(10 + 14 * (1 - t)); g_ = int(12 + 18 * (1 - t)); b_ = int(26 + 44 * (1 - t))
                # stars
                for i in range(14):
                    sx = (i * 0.61803) % 1.0; sy = ((i * 0.7548776) % 0.92) + 0.04
                    if math.hypot(nx - (sx - 0.5), ny - (sy - 0.5)) < 0.006:
                        r_ = g_ = b_ = 235
                # rocket body (vertical, centered, slightly left-leaning flame)
                body_w = 0.055; body_top = -0.30; body_bot = 0.22
                nose = 0.36
                in_body = abs(nx) <= body_w * (1 - max(0, -(ny - body_top)) / 0.2 if ny < body_top else 1) and body_top - (nose - body_top) * 0 <= ny <= body_bot
                # simpler: triangle nose + rect body
                in_nose = abs(nx) <= body_w * (1 - (body_top - ny) / (nose - body_top)) and (nose >= ny >= body_top)
                in_rect = abs(nx) <= body_w and (body_top <= ny <= body_bot)
                # wings
                wing = (0.14 <= ny <= 0.30) and abs(nx) <= 0.05 + (ny - 0.14) * 0.85
                in_body = in_nose or in_rect
                if wing and not in_body:
                    r_, g_, b_ = 39, 75, 255  # wing blue
                elif in_body:
                    if abs(nx) < body_w * 0.45 and ny > body_top:
                        r_, g_, b_ = 39, 117, 255  # stripe
                    else:
                        r_, g_, b_ = 235, 242, 255  # hull
                # engine flame
                if 0.22 <= ny <= 0.42 and abs(nx) <= 0.045 * (1 - (ny - 0.22) / 0.2):
                    r_, g_, b_ = 125, 249, 255
                # edge glow ring
                if r_bg - d < size * 0.02:
                    r_ = min(255, r_ + 40); g_ = min(255, g_ + 70); b_ = min(255, b_ + 90)
                row += [r_, g_, b_, 255]
            else:
                row += [0, 0, 0, 0]
        px.append(row)
    return px

base = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
for dpi, s in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    d = os.path.join(base, f"mipmap-{dpi}")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "ic_launcher.png"), "wb") as f:
        f.write(png(s, s, icon(s)))
    print("wrote", dpi, s)
