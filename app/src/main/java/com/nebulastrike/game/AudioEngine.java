package com.nebulastrike.game;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.nebulastrike.game.core.EnemyType;
import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.PowerUp;
import com.nebulastrike.game.core.Settings;
import com.nebulastrike.game.core.WeaponType;
import com.nebulastrike.game.core.audio.SfxBank;
import com.nebulastrike.game.core.audio.SfxId;
import com.nebulastrike.game.core.audio.SfxMixer;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Procedural audio engine. A dedicated thread mixes synthesized one-shot
 * effects into a streaming AudioTrack, so playback never depends on sample
 * loading or resource compression. Effects are panned by world position and
 * slightly pitch-varied so repeated shots do not sound mechanical.
 */
public class AudioEngine {
    private static final String TAG = "NebulaStrikeAudio";
    private static final int FRAMES = 512;
    private static final float MASTER = 0.9f;
    private static volatile SfxBank bank;

    private final AtomicLongArray lastPlayNs = new AtomicLongArray(SfxId.values().length);
    private Settings settings;
    private Thread thread;
    private volatile boolean running;
    private volatile SfxMixer mixer;
    private volatile AudioTrack track;

    /** Idempotent: called at startup and again after the surface is recreated. */
    public synchronized void init(Settings settings) {
        this.settings = settings;
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "audio-mixer");
        thread.start();
    }

    private void loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        SfxMixer m = new SfxMixer(getBank());
        AudioTrack t = createTrack();
        if (t == null) {
            running = false;
            return;
        }
        mixer = m;
        track = t;
        float[] mix = new float[FRAMES * 2];
        short[] pcm = new short[FRAMES * 2];
        try {
            t.play();
            while (running) {
                boolean muted = settings != null && !settings.sound;
                if (muted) {
                    m.clear();
                    Arrays.fill(mix, 0f);
                } else {
                    m.render(mix, FRAMES);
                }
                for (int i = 0; i < pcm.length; i++) {
                    float v = mix[i] * MASTER;
                    if (v > 1f) v = 1f;
                    else if (v < -1f) v = -1f;
                    pcm[i] = (short) (v * 32767f);
                }
                int written = t.write(pcm, 0, pcm.length);
                if (written < 0) {
                    Log.w(TAG, "AudioTrack write failed: " + written);
                    break;
                }
                if (written == 0) {
                    try { Thread.sleep(5); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "AudioTrack stopped unexpectedly", e);
        } finally {
            track = null;
            mixer = null;
            try { t.stop(); } catch (IllegalStateException ignored) { }
            t.release();
            running = false;
        }
    }

    private static AudioTrack createTrack() {
        int min = AudioTrack.getMinBufferSize(SfxBank.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = FRAMES * 4 * 4;
        int bufferBytes = Math.max(min, FRAMES * 4 * 4);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SfxBank.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                return new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(bufferBytes)
                        .build();
            }
            return new AudioTrack(attrs, format, bufferBytes,
                    AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        } catch (Exception e) {
            Log.w(TAG, "AudioTrack init failed", e);
            return null;
        }
    }

    private static SfxBank getBank() {
        SfxBank b = bank;
        if (b == null) {
            synchronized (AudioEngine.class) {
                b = bank;
                if (b == null) {
                    b = SfxBank.create();
                    bank = b;
                }
            }
        }
        return b;
    }

    /** Presentation mapping for simulation events. */
    public void consume(Iterable<GameWorld.GameEvent> events) {
        for (GameWorld.GameEvent e : events) {
            switch (e.type) {
                case ENEMY_KILLED:
                    if (e.code == EnemyType.TANK.ordinal()) {
                        play(SfxId.EXPLODE_HEAVY, 0.60f, vary(0.07f), panFor(e.x));
                    } else {
                        play(SfxId.EXPLODE, 0.48f, vary(0.10f), panFor(e.x));
                    }
                    break;
                case PLAYER_HIT:
                    play(SfxId.PLAYER_HIT, 0.95f, 1f, panFor(e.x));
                    break;
                case GAME_OVER:
                    play(SfxId.GAME_OVER, 0.90f, 1f, 0f);
                    break;
                case ENEMY_ESCAPED:
                    play(SfxId.ESCAPE, 0.28f, vary(0.10f), panFor(e.x));
                    break;
                case FORMATION:
                    play(SfxId.WAVE, 0.32f, 1f, 0.25f);
                    break;
                case POWERUP_COLLECTED:
                    powerup(e);
                    break;
                case BOMB:
                    play(SfxId.BOMB, 0.95f, 1f, 0f);
                    break;
                case BOSS_SPAWNED:
                    play(SfxId.ALARM, 0.85f, 1f, 0f);
                    break;
                case BOSS_DEFEATED:
                    play(SfxId.BOSS_DOWN, 1f, 1f, 0f);
                    break;
                case BOSS_PHASE:
                    play(SfxId.BOSS_PHASE, 0.75f,
                            1f + Math.max(0, e.code - 1) * 0.06f, 0f);
                    break;
                case BOSS_PART_DESTROYED:
                    play(SfxId.PART_BREAK, 0.80f, vary(0.10f), panFor(e.x));
                    break;
                case BOSS_DISARMED:
                    play(SfxId.SHIELD_BREAK, 0.85f, 0.72f, 0f);
                    break;
                case BOSS_LASER:
                    play(SfxId.BOSS_LASER, 0.90f, 1f, panFor(e.x));
                    break;
                case OVERDRIVE:
                    play(SfxId.OVERDRIVE, 0.92f, 1f, panFor(e.x));
                    break;
                case GRAZE:
                    play(SfxId.GRAZE, 0.20f, vary(0.16f), panFor(e.x));
                    break;
                case SHIELD_BROKEN:
                    play(SfxId.SHIELD_BREAK, 0.82f, 1f, panFor(e.x));
                    break;
                case COMBO:
                    play(SfxId.COMBO, 0.48f,
                            1f + Math.min(0.6f, Math.max(0, e.code) * 0.03f),
                            panFor(e.x));
                    break;
                case DASH:
                    play(SfxId.DASH, 0.62f, vary(0.06f), panFor(e.x));
                    break;
                case CHAIN:
                    play(SfxId.CHAIN_ZAP, 0.72f,
                            1f + Math.min(0.35f, Math.max(0, e.code) * 0.05f), panFor(e.x));
                    break;
                case FUSION:
                    play(SfxId.FUSION, 0.80f, 1f, panFor(e.x));
                    break;
                case BOSS_RAM:
                    play(SfxId.BOSS_RAM, 0.90f, vary(0.04f), panFor(e.x));
                    break;
                case HEAL_PULSE:
                    play(SfxId.HEAL, 0.42f, vary(0.08f), panFor(e.x));
                    break;
                case ORBIT_HIT:
                    play(SfxId.ORBIT, 0.24f, vary(0.18f), panFor(e.x));
                    break;
                case RUSH:
                    play(SfxId.ALARM, 0.55f, 1.25f, 0f);
                    break;
                case NEW_SHIP_DEBUT:
                    // 比 Boss 警报低一档：新舰型登场是提醒，不是威胁
                    play(SfxId.ALARM, 0.70f, 0.9f, 0f);
                    break;
                case NOVA:
                    play(SfxId.NOVA, 0.98f, vary(0.05f), 0f);
                    break;
                case TURRET_DEPLOY:
                    play(SfxId.TURRET_DEPLOY, 0.72f, vary(0.06f), panFor(e.x));
                    break;
                case RELIC_GAINED:
                    play(SfxId.RELIC, 1f, 1f + Math.min(0.25f, Math.max(0, e.code) * 0.05f), 0f);
                    break;
                case RESPAWN:
                    play(SfxId.RESPAWN, 0.80f, 1f, 0f);
                    break;
                case MINE:
                    play(SfxId.MINE, 0.40f, vary(0.14f), panFor(e.x));
                    break;
                case SHIELD_BLOCK:
                    // code==1 表示护盾被击穿，用更亮的破碎声收尾
                    play(e.code == 1 ? SfxId.SHIELD_BREAK : SfxId.SHIELD_BLOCK,
                            e.code == 1 ? 0.70f : 0.42f, vary(0.10f), panFor(e.x));
                    break;
                case CRIT:
                    // 暴击只点一下，伤害越高越亮；限流见 minIntervalNs
                    play(SfxId.CRIT, 0.55f, 1f + Math.min(0.35f, e.code * 0.02f), panFor(e.x));
                    break;
                case BULLET_BREAK:
                    // code==1 是点爆水雷：更低更闷，提醒玩家这不是普通弹
                    play(SfxId.BULLET_POP, e.code == 1 ? 0.75f : 0.5f,
                            e.code == 1 ? 0.72f : vary(0.10f), panFor(e.x));
                    break;
                case PARRY:
                    // code = 本次冲刺内的连击序号；连得越高音越亮，一次冲刺里要听得见层次
                    play(SfxId.PARRY, 0.65f + Math.min(0.3f, e.code * 0.06f),
                            1f + Math.min(0.6f, (e.code - 1) * 0.12f), panFor(e.x));
                    break;
                case MUTATOR_START:
                    // 异变波开场警报：居中、响亮，玩家必须知道规则已经变了
                    play(SfxId.MUTATOR, 0.85f, 1f, 0f);
                    break;
                case MUTATOR_CLEAR:
                    // code = 奖励分数：给得越多铃越亮
                    play(SfxId.MUTATOR_CLEAR, 0.7f,
                            1f + Math.min(0.35f, e.code / 1200f), 0f);
                    break;
                case GRAVITY_DEPLOY:
                    play(SfxId.GRAVITY, 0.90f, vary(0.05f), panFor(e.x));
                    break;
                case GRAVITY_ABSORB:
                    // code = 该井当前库存：越吸越满，音高逐级抬升，喷还前就能听出"快满了"
                    play(SfxId.GRAVITY_ABSORB, 0.5f + Math.min(0.25f, e.code * 0.02f),
                            1f + Math.min(0.55f, (e.code - 1) * 0.09f), panFor(e.x));
                    break;
                case GRAVITY_BURST:
                    // code = 喷出的弹数：一次喷得越多越响
                    play(SfxId.GRAVITY_BURST, 0.6f + Math.min(0.35f, e.code * 0.03f),
                            vary(0.06f), panFor(e.x));
                    break;
                case ULT_READY:
                    play(SfxId.ULT_READY, 0.75f, 1f, 0f);
                    break;
                case ULT_CAST:
                    // code = 档位：憋满的第三档要明显更沉
                    play(SfxId.ULT_CAST, 0.7f + 0.1f * Math.max(1, e.code),
                            1f - 0.07f * Math.max(0, 3 - e.code), 0f);
                    break;
                case ULT_LANCE:
                    // code = 实际放出的矛数，空场放空矛时声音更薄
                    if (e.code > 0) {
                        play(SfxId.ULT_LANCE, 0.4f + Math.min(0.4f, e.code * 0.02f),
                                vary(0.08f), panFor(e.x));
                    }
                    break;
                case MIRAGE_REVEAL:
                    play(SfxId.MIRAGE_REVEAL, 0.82f, vary(0.07f), panFor(e.x));
                    break;
                case MAG_BURST:
                    // code > 0 = 放出 EMP 环；code < 0 = 磁暴艇被击毁、磁场坍缩吞弹
                    if (e.code > 0) {
                        play(SfxId.EMP_BURST, 0.6f + Math.min(0.3f, e.code * 0.04f),
                                vary(0.06f), panFor(e.x));
                    } else if (e.code < 0) {
                        play(SfxId.GRAVITY_ABSORB, 0.6f, 0.8f, panFor(e.x));
                        play(SfxId.SHIELD_BREAK, 0.4f, vary(0.05f), panFor(e.x));
                    }
                    break;
                case BROOD_SPAWN:
                    play(SfxId.BROOD_CHIRP, 0.55f + Math.min(0.25f, e.code * 0.08f),
                            vary(0.09f), panFor(e.x));
                    break;
                case ACHIEVEMENT_UNLOCKED:
                    // 居中不 pans：成就属于整局，不属于屏幕左边还是右边
                    play(SfxId.ACHIEVEMENT, 0.86f, 1f, 0f);
                    break;
            }
        }
    }

    private void powerup(GameWorld.GameEvent e) {
        PowerUp.Kind[] kinds = PowerUp.Kind.values();
        PowerUp.Kind kind = e.code >= 0 && e.code < kinds.length ? kinds[e.code] : null;
        if (kind == PowerUp.Kind.LIFE) {
            play(SfxId.EXTRA_LIFE, 0.85f, 1f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.SPREAD) {
            play(SfxId.SHOOT_SPREAD, 0.55f, 1.25f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.MISSILE) {
            play(SfxId.SHOOT_MISSILE, 0.55f, 1.25f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.LASER) {
            play(SfxId.SHOOT_LASER, 0.55f, 1.25f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.ORBIT) {
            play(SfxId.ORBIT, 0.75f, 1.1f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.CHAIN) {
            play(SfxId.CHAIN_ZAP, 0.70f, 1.1f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.BONUS) {
            play(SfxId.BONUS, 0.92f, 1f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.BOUNCE) {
            play(SfxId.SHOOT_BOUNCE, 0.75f, 1.2f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.NOVA) {
            play(SfxId.NOVA, 0.95f, 1f, 0f);
            return;
        }
        if (kind == PowerUp.Kind.NANITE) {
            play(SfxId.HEAL, 0.70f, 1.15f, panFor(e.x));
            return;
        }
        if (kind == PowerUp.Kind.TURRET) {
            play(SfxId.TURRET_DEPLOY, 0.80f, 1f, panFor(e.x));
            return;
        }
        float pitch = kind == PowerUp.Kind.SHIELD ? 1.18f : 1f;
        play(SfxId.POWERUP, 0.65f, pitch, panFor(e.x));
    }

    /** Weapon-aware volley sound; called once per simulation volley. */
    public void shoot(WeaponType weapon, float x) {
        switch (weapon) {
            case SPREAD:
                play(SfxId.SHOOT_SPREAD, 0.46f, vary(0.05f), panFor(x));
                break;
            case LASER:
                play(SfxId.SHOOT_LASER, 0.38f, vary(0.05f), panFor(x));
                break;
            case MISSILE:
                play(SfxId.SHOOT_MISSILE, 0.52f, vary(0.05f), panFor(x));
                break;
            case LANCE:
                play(SfxId.SHOOT_LASER, 0.62f, 0.62f, panFor(x));
                break;
            case CLUSTER:
                play(SfxId.SHOOT_SPREAD, 0.58f, 0.8f, panFor(x));
                break;
            case SWARM:
                play(SfxId.SHOOT_LASER, 0.34f, 1.5f, panFor(x));
                break;
            case BOUNCE:
                play(SfxId.SHOOT_BOUNCE, 0.40f, vary(0.07f), panFor(x));
                break;
            default:
                play(SfxId.SHOOT_VULCAN, 0.40f, vary(0.06f), panFor(x));
                break;
        }
    }

    public void wingmanShot(float x) {
        play(SfxId.WINGMAN, 0.16f, vary(0.12f), panFor(x));
    }

    /** 冲刺能量不足时的空挡反馈。 */
    public void dashBlocked() {
        play(SfxId.UI, 0.18f, 0.6f, 0f);
    }

    /** 终极槽还没攒够时的空挡反馈：比冲刺更低更闷，避免误以为"坏了"。 */
    public void ultBlocked() {
        play(SfxId.UI, 0.22f, 0.45f, 0f);
    }

    public void uiTick() {
        play(SfxId.UI, 0.30f, vary(0.08f), 0f);
    }

    /** Confirmation chime when sound is switched back on. */
    public void confirmSound() {
        play(SfxId.POWERUP, 0.60f, 1.35f, 0f);
    }

    private void play(SfxId id, float volume, float pitch, float panValue) {
        if (settings != null && !settings.sound) return;
        long now = System.nanoTime();
        int idx = id.ordinal();
        long last = lastPlayNs.get(idx);
        if (now - last < minGapNs(id)) return;
        lastPlayNs.set(idx, now);
        SfxMixer m = mixer;
        if (m != null) m.play(id, volume, pitch, panValue);
    }

    private static long minGapNs(SfxId id) {
        switch (id) {
            case SHOOT_VULCAN: return 28_000_000L;
            case SHOOT_SPREAD: return 40_000_000L;
            case SHOOT_LASER: return 34_000_000L;
            case SHOOT_MISSILE: return 60_000_000L;
            case WINGMAN: return 80_000_000L;
            case EXPLODE: return 30_000_000L;
            case EXPLODE_HEAVY: return 50_000_000L;
            case GRAZE: return 45_000_000L;
            case COMBO: return 90_000_000L;
            case PART_BREAK: return 55_000_000L;
            case BOSS_LASER: return 120_000_000L;
            case UI: return 40_000_000L;
            case ORBIT: return 45_000_000L;
            case HEAL: return 160_000_000L;
            case DASH: return 120_000_000L;
            case CHAIN_ZAP: return 200_000_000L;
            case FUSION: return 300_000_000L;
            case BOSS_RAM: return 450_000_000L;
            case BONUS: return 300_000_000L;
            case SHOOT_BOUNCE: return 46_000_000L;
            case NOVA: return 600_000_000L;
            case TURRET_DEPLOY: return 320_000_000L;
            case RELIC: return 500_000_000L;
            case RESPAWN: return 400_000_000L;
            case MINE: return 140_000_000L;
            case SHIELD_BLOCK: return 70_000_000L;
            case CRIT: return 130_000_000L;   // 暴击频发，130ms 内只点一次
            case BULLET_POP: return 55_000_000L;   // 点弹幕是连续动作，允许较快连点
            case PARRY: return 45_000_000L;        // 一次冲刺可能连弹好几发，留出层次
            case MUTATOR: return 0L;               // 三十几秒一次，永不抑制
            case MUTATOR_CLEAR: return 0L;         // 同上
            case GRAVITY: return 300_000_000L;     // 一口井八秒多才再部署
            case GRAVITY_ABSORB: return 55_000_000L;  // 吸弹是连续动作，限流后仍有"连吞"层次
            case GRAVITY_BURST: return 220_000_000L;
            case ULT_READY: return 0L;              // 攒满才响一次，不需要限流
            case ULT_CAST: return 0L;               // 大招本身是稀有事件
            case ULT_LANCE: return 0L;
            case MIRAGE_REVEAL: return 90_000_000L;  // 一群伪装者同时现形时别糊成一片
            case EMP_BURST: return 120_000_000L;
            case BROOD_CHIRP: return 150_000_000L;
            case ACHIEVEMENT: return 500_000_000L;   // 一次结算连解两条时只吹一记号角
            default: return 0L;
        }
    }

    private static float vary(float amount) {
        return 1f + (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * amount;
    }

    private static float panFor(float x) {
        float p = x / GameWorld.WIDTH * 2f - 1f;
        return p * 0.7f;
    }

    public synchronized void release() {
        if (!running && thread == null) return;
        running = false;
        AudioTrack t = track;
        if (t != null) {
            try { t.stop(); } catch (IllegalStateException ignored) { }
        }
        Thread th = thread;
        thread = null;
        if (th != null) {
            try { th.join(800); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        track = null;
        mixer = null;
    }
}
