# NebulaStrike

A fully **offline** arcade space shooter for Android. Zero third-party dependencies —
pure Java on the Android framework (`SurfaceView` + `Canvas`), so it builds anywhere
Android Studio runs and ships as a tiny APK.

## Gameplay
- Drag anywhere to fly; your ship fires automatically.
- Survive escalating waves of drones, weavers and hunters.
- Score points, dodge, repeat.

## Project layout
```
app/src/main/java/com/nebulastrike/game/
  core/     pure-Java simulation (no android.*): entities, spawning, collisions
  render/   Canvas renderer (virtual 1600x900 space, letterboxed to any screen)
  GameView  game thread + touch input      MainActivity  entry point
app/src/test/   JUnit tests for the simulation (run on the JVM)
scripts/verify.sh  SDK-less verification: type-check + unit tests
```

## Build
```bash
./gradlew :app:assembleDebug     # APK at app/build/outputs/apk/debug/
```

## Verify (no Android SDK required)
```bash
scripts/verify.sh                # javac vs official android.jar + JUnit tests
```

License: MIT.
