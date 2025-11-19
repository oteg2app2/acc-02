package com.example.acc_02;

import android.content.Context;
import android.content.SharedPreferences;

// SettingsManager.java

public class SettingsManager {
    private static final String PREF_NAME = "game_settings";
    private static final String KEY_HIGH_SCORE = "high_score";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_VIBRATION_ON = "vibration_on";
    private static final String KEY_TUTORIAL_SHOWN = "tutorial_shown";

    private SharedPreferences prefs;
    private int highScore;
    private float currentVolume;
    private boolean vibrationOn;
    private boolean tutorialShown;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadSettings();
    }

    private void loadSettings() {
        highScore = prefs.getInt(KEY_HIGH_SCORE, 0);
        currentVolume = prefs.getFloat(KEY_VOLUME, 0.5f); // デフォルト音量 50%
        vibrationOn = prefs.getBoolean(KEY_VIBRATION_ON, true); // デフォルトで振動ON
        tutorialShown = prefs.getBoolean(KEY_TUTORIAL_SHOWN, false); // ★追加: デフォルトでチュートリアル未表示
    }

    public void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_HIGH_SCORE, highScore);
        editor.putFloat(KEY_VOLUME, currentVolume);
        editor.putBoolean(KEY_VIBRATION_ON, vibrationOn);
        editor.putBoolean(KEY_TUTORIAL_SHOWN, tutorialShown); // ★追加: チュートリアル状態を保存
        editor.apply();
    }

    public int getHighScore() {
        return highScore;
    }

    public void saveHighScore(int newScore) {
        if (newScore > highScore) {
            this.highScore = newScore;
            saveSettings(); // ハイスコア更新時は設定を保存
        }
    }

    public void loadHighScore() {
        this.highScore = prefs.getInt(KEY_HIGH_SCORE, 0);
    }


    public float getCurrentVolume() {
        return currentVolume;
    }

    public void setCurrentVolume(float currentVolume) {
        this.currentVolume = currentVolume;
    }

    public boolean isVibrationOn() {
        return vibrationOn;
    }

    public void setVibrationOn(boolean vibrationOn) {
        this.vibrationOn = vibrationOn;
    }

    // チュートリアル表示状態のゲッター
    public boolean isTutorialShown() {
        return tutorialShown;
    }

    // チュートリアル表示状態のセッター
    public void setTutorialShown(boolean shown) {
        this.tutorialShown = shown;
        saveSettings(); // チュートリアル状態変更時は設定を保存
    }
}