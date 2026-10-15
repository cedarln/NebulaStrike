# NebulaStrike

A fully **offline** arcade space shooter for Android. Zero third-party dependencies —
pure Java on the Android framework (`SurfaceView` + `Canvas`), so it builds anywhere
Android Studio runs and ships as a tiny (~280 KB) APK.

## Gameplay
- Drag anywhere to fly; your ship fires automatically. Second finger = smart bomb.
- Fight drones, weavers, hunters and tanks in V-formations.
- Weapons & power-ups: spread shot, homing missiles, shields, extra bombs.
- **Boss battles**: DREADNOUGHT capital ships with 3 phases, bullet patterns and minion summons.
- Combo multiplier rewards risky kill chains; screen shake, particles and synthesized 8-bit SFX.
- Local top-5 leaderboard with best-score persistence; pause/settings/game-over UI flow.
- Difficulty ramps over 2.5 minutes, then creeps; boss waves every 4 levels.

## Project layout
```
app/src/main/java/com/nebulastrike/game/
  core/     pure-Java simulation (no android.*): entities, spawning, collisions,
            boss AI, difficulty curve, adaptive quality governor
  render/   Canvas renderer (virtual 1600x900 space, letterboxed to any screen),
            zero per-frame allocation (shared Path, cached gradients)
  AudioEngine  SoundPool SFX (synthesized WAVs in res/raw)   Haptics  vibration
  GameView  game thread + touch input      MainActivity  entry point
app/src/test/   JUnit tests for the simulation (run on the JVM)
scripts/gen_sfx.py  synthesizes all sound effects (pure Python, no assets)
scripts/verify.sh   SDK-less verification: type-check + unit tests + sim benchmark
```

## Performance
- Fixed virtual coordinate system, object pools for all entities, fixed timestep.
- Frame governor: 60 fps in game, 30 fps in menus; adaptive particle emission via
  frame-cost feedback; low-RAM devices start with reduced fx.
- `scripts/verify.sh` step 4 runs a 60-second worst-case simulation benchmark
  (~5 us/frame on the CI CPU — ~18x headroom over the 60 fps budget).

## Build
```bash
./gradlew :app:assembleDebug     # APK at app/build/outputs/apk/debug/
```

## Verify (no Android SDK required)
```bash
scripts/verify.sh                # javac vs official android.jar + JUnit tests + bench
```

License: MIT.
