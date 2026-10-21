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

    /**
     * 系统返回键 / 返回手势的唯一入口。
     * 早期版本只靠 GameView.onKeyDown 处理，但 SurfaceView 未必拿到焦点，
     * 返回键就落到 Activity 的默认行为（finish()）上——表现即"一局打到一半，按返回直接退回桌面"。
     * 这里把决策交给 GameWorld.consumeBack()：能消化就不退出，主菜单裸界面才允许退出应用。
     */
    @Override
    public void onBackPressed() {
        if (gameView != null && gameView.getWorld().consumeBack()) {
            gameView.playBackUiSound();
            return;
        }
        super.onBackPressed();
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
