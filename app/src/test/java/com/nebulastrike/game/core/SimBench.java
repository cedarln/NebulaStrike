package com.nebulastrike.game.core;

/**
 * Standalone JVM micro-benchmark for the simulation core (not a JUnit test).
 * Run via scripts/verify.sh step 4. Simulates 60 seconds of worst-case
 * gameplay and fails the build if a single update() cannot comfortably fit
 * a 60fps frame budget on a modest CPU.
 */
public final class SimBench {
    private SimBench() {}

    public static void main(String[] args) {
        GameWorld w = new GameWorld(42L);
        w.spawningEnabled = true;
        w.lives = 999_999; // keep the run alive for the full window
        w.ship.makeInvulnerable();

        int frames = 60 * 60; // 60 simulated seconds
        long t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            // steer a lazy figure-eight to exercise bullets, collisions, particles
            float a = (float) (i * 0.05);
            w.moveShipTo(800f + (float) Math.cos(a) * 500f,
                    450f + (float) Math.sin(a * 2f) * 220f);
            w.update(1f / 60f);
            w.pollEvents();
        }
        long elapsed = System.nanoTime() - t0;

        double perFrameUs = elapsed / 1000.0 / frames;
        double simFps = 1_000_000_000.0 / elapsed * frames;
        int kills = w.kills;
        int particles = w.activeParticles();
        System.out.printf("SIM BENCH: %d frames in %.1f ms | %.0f us/frame | %.0f fps headroom | %d kills | %d particles%n",
                frames, elapsed / 1e6, perFrameUs, simFps, kills, particles);

        // worst frame must fit ~1/3 of a 16.6ms budget on any half-decent CPU
        if (perFrameUs > 5500) {
            System.err.println("FAIL: simulation too slow (" + perFrameUs + " us/frame)");
            System.exit(1);
        }
        System.out.println("SIM BENCH OK");
    }
}
