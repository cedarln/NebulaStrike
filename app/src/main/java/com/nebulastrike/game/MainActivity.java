package com.nebulastrike.game;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** Entry point: hosts the fullscreen game view. */
public class MainActivity extends Activity {
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 音量键直接控制游戏音效所在的媒体音量
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        gameView = new GameView(this);
        setContentView(gameView);
        hideSystemUi();
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.getWorld().pauseIfPlaying();
    }

    GameView getGameView() {
        return gameView;
    }
}
