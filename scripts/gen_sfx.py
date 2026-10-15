#!/usr/bin/env python3
"""Synthesizes retro-style SFX WAVs into app/src/main/res/raw/ (no deps)."""
import math, os, random, struct, wave

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

def write_wav(name, samples):
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = b"".join(struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples)
        w.writeframes(frames)
    print("wrote", name, len(samples) / SR, "s")

def env(i, n, a=0.005, r=0.6):
    t = i / SR
    T = n / SR
    attack = min(1.0, t / a) if a > 0 else 1.0
    release = (1 - t / T) ** r
    return attack * release

def square(ph): return 1.0 if (ph % 1.0) < 0.5 else -1.0
def tri(ph): p = ph % 1.0; return 4 * p - 1 if p < 0.5 else 3 - 4 * p

def shoot():
    n = int(SR * 0.08)
    out = []
    for i in range(n):
        t = i / SR
        f = 900 - 5500 * t
        s = square(f * t) * 0.5 + math.sin(2 * math.pi * f * t) * 0.5
        out.append(s * env(i, n, 0.002, 1.2) * 0.5)
    return out

def explosion(big=False):
    dur = 0.6 if big else 0.32
    n = int(SR * dur)
    rnd = random.Random(42)
    out, lp = [], 0.0
    for i in range(n):
        t = i / SR
        f0 = (120 if big else 220) * (1 - t / dur)
        noise = rnd.uniform(-1, 1) * 0.6
        lp += 0.25 * (noise - lp)
        s = lp + math.sin(2 * math.pi * f0 * t) * 0.5
        out.append(s * env(i, n, 0.001, 2.2) * (0.9 if big else 0.6))
    return out

def hit():
    n = int(SR * 0.18)
    out, lp = [], 0.0
    rnd = random.Random(7)
    for i in range(n):
        t = i / SR
        noise = rnd.uniform(-1, 1) * 0.4
        lp += 0.3 * (noise - lp)
        s = lp + math.sin(2 * math.pi * (90 - 200 * t) * t) * 0.7
        out.append(s * env(i, n, 0.001, 1.5) * 0.7)
    return out

def powerup():
    notes = [523, 659, 784, 1046]  # C5 E5 G5 C6
    out = []
    for k, f in enumerate(notes):
        n = int(SR * 0.07)
        for i in range(n):
            s = tri(f * i / SR) * 0.7 + math.sin(2 * math.pi * f * i / SR) * 0.3
            out.append(s * env(i, n, 0.004, 0.8) * 0.55)
    return out

def bomb():
    n = int(SR * 0.9)
    rnd = random.Random(11)
    out, lp = [], 0.0
    for i in range(n):
        t = i / SR
        noise = rnd.uniform(-1, 1) * 0.5
        lp += 0.12 * (noise - lp)
        s = lp * 0.7 + math.sin(2 * math.pi * (55 + 40 * math.exp(-t * 6)) * t) * 0.8
        out.append(s * env(i, n, 0.002, 1.6))
    return out

def alarm():
    out = []
    for k in range(3):
        f = 660 if k % 2 == 0 else 440
        n = int(SR * 0.14)
        for i in range(n):
            s = square(f * i / SR) * 0.4 + tri(f * 2 * i / SR) * 0.3
            out.append(s * env(i, n, 0.004, 0.7) * 0.5)
    return out

def gameover():
    notes = [392, 330, 262, 196]  # G4 E4 C4 G3
    out = []
    for f in notes:
        n = int(SR * 0.22)
        for i in range(n):
            s = math.sin(2 * math.pi * f * i / SR) * 0.7 + tri(f / 2 * i / SR) * 0.3
            out.append(s * env(i, n, 0.005, 1.1) * 0.55)
    return out

def ui():
    n = int(SR * 0.045)
    out = []
    for i in range(n):
        s = tri(1400 * i / SR)
        out.append(s * env(i, n, 0.001, 0.9) * 0.4)
    return out

def bossdown():
    n = int(SR * 1.3)
    rnd = random.Random(99)
    out, lp = [], 0.0
    for i in range(n):
        t = i / SR
        noise = rnd.uniform(-1, 1) * 0.55
        lp += 0.1 * (noise - lp)
        s = lp * 0.6 + math.sin(2 * math.pi * (48 + 30 * math.exp(-t * 4)) * t) * 0.85
        s += math.sin(2 * math.pi * 220 * t) * math.exp(-t * 3) * 0.2
        out.append(s * env(i, n, 0.002, 1.4))
    return out

write_wav("sfx_shoot", shoot())
write_wav("sfx_explode", explosion())
write_wav("sfx_big_explode", explosion(True))
write_wav("sfx_hit", hit())
write_wav("sfx_powerup", powerup())
write_wav("sfx_bomb", bomb())
write_wav("sfx_alarm", alarm())
write_wav("sfx_gameover", gameover())
write_wav("sfx_ui", ui())
write_wav("sfx_bossdown", bossdown())
