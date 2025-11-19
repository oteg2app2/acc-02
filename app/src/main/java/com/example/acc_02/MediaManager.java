package com.example.acc_02;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;

/**
 * BGMと効果音の再生を管理するクラス。
 */
public class MediaManager {

    private final Context context;

    // BGM
    private MediaPlayer mpMenu, mpGame, mpGameOver;
    private float currentVolume = 0.5f;

    // 効果音
    private SoundPool soundPool;
    private int hitSoundId;
    private int clickSoundId;
    private int levelUpSoundId;

    public MediaManager(Context context) {
        this.context = context;
        initializeMedia();
    }

    /**
     * BGMと効果音（SoundPool）を初期化する。
     */
    private void initializeMedia() {
        // BGMの初期化
        mpMenu = MediaPlayer.create(context, R.raw.bgm_menu);
        mpMenu.setLooping(true);
        mpGame = MediaPlayer.create(context, R.raw.bgm_game);
        mpGame.setLooping(true);
        mpGameOver = MediaPlayer.create(context, R.raw.bgm_gameover);
        mpGameOver.setLooping(true);

        // SoundPoolの初期化
        soundPool = new SoundPool.Builder().setMaxStreams(5).build();
        hitSoundId = soundPool.load(context, R.raw.hit_sound, 1);
        clickSoundId = soundPool.load(context, R.raw.click_sound, 1);
        levelUpSoundId = soundPool.load(context, R.raw.levelup_sound, 1);
    }

    /**
     * BGMを切り替え、すべてのBGMに現在の音量を適用する。
     * @param newBgm 再生を開始するMediaPlayerインスタンス
     */
    public void startBGM(MediaPlayer newBgm) {
        adjustVolume(currentVolume);

        if (mpMenu != null && mpMenu.isPlaying()) { mpMenu.pause(); mpMenu.seekTo(0); }
        if (mpGame != null && mpGame.isPlaying()) { mpGame.pause(); mpGame.seekTo(0); }
        if (mpGameOver != null && mpGameOver.isPlaying()) { mpGameOver.pause(); mpGameOver.seekTo(0); }

        if (newBgm != null) {
            newBgm.start();
        }
    }

    /**
     * BGMの再生を一時停止する。
     * (MainActivityのポーズボタン押下時に使用。一時停止位置を保持する。)
     */
    public void pauseBGM() { // ★ このメソッドを追加
        // 再生中のBGMを一時停止（位置は維持）
        if (mpMenu != null && mpMenu.isPlaying()) { mpMenu.pause(); }
        if (mpGame != null && mpGame.isPlaying()) { mpGame.pause(); }
        if (mpGameOver != null && mpGameOver.isPlaying()) { mpGameOver.pause(); }
    }

    /**
     * BGMの音量を調整する。
     * @param volume 0.0f (ミュート) から 1.0f (最大) までの音量
     */
    public void adjustVolume(float volume) {
        this.currentVolume = volume;
        if (mpMenu != null) mpMenu.setVolume(volume, volume);
        if (mpGame != null) mpGame.setVolume(volume, volume);
        if (mpGameOver != null) mpGameOver.setVolume(volume, volume);
    }

    /**
     * ボタンクリック効果音を再生する。
     */
    public void playClickSound() {
        if (soundPool != null) {
            soundPool.play(clickSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    /**
     * ヒット効果音を再生する。
     */
    public void playHitSound() {
        if (soundPool != null) {
            soundPool.play(hitSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    /**
     * レベルアップ効果音を再生する。
     */
    public void playLevelUpSound() {
        if (soundPool != null) {
            soundPool.play(levelUpSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    // --- ライフサイクル対応 ---

    public void onPause() {
        if (mpMenu != null) mpMenu.pause();
        if (mpGame != null) mpGame.pause();
        if (mpGameOver != null) mpGameOver.pause();
        if (soundPool != null) soundPool.autoPause();
    }

    public void onResume() {
        if (soundPool != null) soundPool.autoResume();
    }

    public void release() {
        if (mpMenu != null) { mpMenu.release(); mpMenu = null; }
        if (mpGame != null) { mpGame.release(); mpGame = null; }
        if (mpGameOver != null) { mpGameOver.release(); mpGameOver = null; }
        if (soundPool != null) { soundPool.release(); soundPool = null; }
    }

    // --- ゲッター ---

    public MediaPlayer getMpMenu() { return mpMenu; }
    public MediaPlayer getMpGame() { return mpGame; }
    public MediaPlayer getMpGameOver() { return mpGameOver; }
    public float getCurrentVolume() { return currentVolume; }
}