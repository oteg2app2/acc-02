package com.example.acc_02;

import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;

import com.example.acc_02.GameManager.GameCallback;

import java.util.List;
import android.util.Log;

import com.google.firebase.BuildConfig;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * メインアクティビティ。UI操作、センサー入力処理、
 * 各種マネージャークラスとの連携を担当する。
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener, GameCallback {

    // --- マネージャー/コアロジック ---
    private SettingsManager settingsManager;
    private MediaManager mediaManager;
    private GameManager gameManager;

    // --- センサー関連 ---
    private SensorManager sensorManager;
    private Vibrator vibrator;

    // --- UI要素 ---
    private ImageView playerImage;
    private TextView gameOverText;
    private TextView levelInfoText;
    private TextView scoreText;
    private TextView highScoreText;
    // ★修正: levelButtonsはLinearLayout型に変更
    private Button restartButton, easyButton, normalButton, hardButton, levelsButton, settingsCloseButton;
    private ImageButton settingsButton;
    // ★修正: levelButtonsをLinearLayoutの宣言に追加
    private LinearLayout gameOverButtons, scorePanel, levelButtons;
    // ★追加: チュートリアル関連のFrameLayout、TextView、Buttonを追加
    private FrameLayout gameOverPanel, raindropContainer, settingsPanel, tutorialPanel;
    private TextView tutorialText;
    private Button tutorialStartButton;

    private SeekBar volumeSeekBar;
    private Switch vibrationSwitch;
    private ViewGroup mainLayout;
    private ImageButton pauseButton;
    private boolean isGamePaused = false;
    private boolean wasGameRunningBeforePause = false;

    // --- ゲーム設定値（難易度選択時にセット） ---
    private int selectedRainSpawnChance = 5;
    private int selectedScoreMultiplier = 3;

    private static final float PLAYER_SPEED_FACTOR = 50.0f;
    private static final float SCORE_SCALE_FACTOR = 1.2f;
    private static final int SCORE_ANIM_DURATION = 200;
    private final Handler scoreAnimHandler = new Handler(Looper.getMainLooper());

    // Firebase関連の変数
    private FirebaseAnalytics mFirebaseAnalytics;
    private FirebaseRemoteConfig remoteConfig;
    // デバッグ時は即時取得（0）、本番時はキャッシュを利用
    private final long CACHE_EXPIRATION_SECONDS = BuildConfig.DEBUG ? 0 : 3600;


    // =========================================================================================
    // ライフサイクルメソッド
    // =========================================================================================

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (mediaManager != null) mediaManager.onPause();
        // isGamePaused はユーザーがボタンを押したかどうか
        if (gameManager != null) {
            if (!isGamePaused && levelButtons.getVisibility() == View.GONE && gameOverPanel.getVisibility() == View.GONE) {
                wasGameRunningBeforePause = true;
            }
            // ★修正: ライフサイクルの一時停止では、雨粒を消さない pauseLoopOnly() を呼ぶ
            // gameManager.pauseGame(); // <-- 削除
            gameManager.pauseLoopOnly(); // <-- これに戻す
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ★修正: チュートリアル画面もアクティブ画面ではないと見なす
        final boolean isGameActiveScreen = (levelButtons.getVisibility() == View.GONE && gameOverPanel.getVisibility() == View.GONE && tutorialPanel.getVisibility() == View.GONE);
        boolean shouldResumeAfterLifecycle = isGameActiveScreen && !isGamePaused && wasGameRunningBeforePause;

        // センサー再開（ポーズ中でもセンサーは必要）
        if (isGameActiveScreen) {
            List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
            if (sensors.size() > 0) {
                Sensor s = sensors.get(0);
                sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        // BGM再開（ポーズ中は再開しない）
        if (mediaManager != null) {
            MediaPlayer newBgm = null;
            // ★修正: チュートリアル画面もメニューBGM
            if (settingsPanel.getVisibility() == View.VISIBLE || levelButtons.getVisibility() == View.VISIBLE || tutorialPanel.getVisibility() == View.VISIBLE) {
                newBgm = mediaManager.getMpMenu();
            } else if (gameOverPanel.getVisibility() == View.VISIBLE) {
                newBgm = mediaManager.getMpGameOver();
            } else if (shouldResumeAfterLifecycle) {
                newBgm = mediaManager.getMpGame();
            }

            if (newBgm != null) mediaManager.startBGM(newBgm);
            mediaManager.onResume();
        }

        // ゲームループ再開
        if (gameManager != null) {
            if (shouldResumeAfterLifecycle) {
                gameManager.startGameLoop();
            }
        }


        wasGameRunningBeforePause = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // UI要素の初期化
        initializeUIComponents();

        // マネージャーの初期化
        settingsManager = new SettingsManager(this);
        mediaManager = new MediaManager(this);
        gameManager = new GameManager(this, raindropContainer, mainLayout, playerImage, this);

        // Firebase Analyticsの初期化
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // Firebase Remote Configの初期化と設定
        remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(CACHE_EXPIRATION_SECONDS)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);

        // Remote Configの値のフェッチと適用
        fetchRemoteConfig();

        // 設定の適用とUI反映
        mediaManager.adjustVolume(settingsManager.getCurrentVolume());
        updateHighScoreUI();

        // 起動時に振動スイッチの初期状態をロードした設定値に合わせる
        vibrationSwitch.setChecked(settingsManager.isVibrationOn());

        // 起動直後の画面状態をレベル選択画面に設定
        scorePanel.setVisibility(View.GONE);
        playerImage.setVisibility(View.GONE);
        raindropContainer.setVisibility(View.GONE);
        levelButtons.setVisibility(View.VISIBLE);
        settingsButton.setVisibility(View.VISIBLE);

        // ★追加: チュートリアル/設定パネルを初期状態で非表示に設定
        tutorialPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);

        // UIイベントリスナーの設定
        setupEventListeners();

        // ★追加: チュートリアル表示判定
        if (!settingsManager.isTutorialShown()) {
            showTutorial(); // まだ表示されていない場合はチュートリアルを表示
        } else {
            // チュートリアルが既に表示済みの場合は、メニューBGMを開始
            mediaManager.startBGM(mediaManager.getMpMenu());
        }
    }

    // ★追加: Remote Configのフェッチメソッド
    /**
     * Firebase Remote Configから最新の設定値を取得し、適用する。
     */
    private void fetchRemoteConfig() {
        remoteConfig.fetchAndActivate()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d("RemoteConfig", "Fetch and activate succeeded. Values are now available.");
                    } else {
                        Log.d("RemoteConfig", "Fetch failed. Using default values.");
                    }
                    // フェッチ結果に関わらず、ボタンのクリックイベントで最新の値が使われる
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaManager != null) mediaManager.release();
    }

    // =========================================================================================
    // 初期化とUI操作
    // =========================================================================================

    /**
     * すべてのUIコンポーネントを findViewById で初期化する。
     */
    private void initializeUIComponents() {
        playerImage = findViewById(R.id.imageView1);
        gameOverText = findViewById(R.id.gameOverText);
        levelInfoText = findViewById(R.id.levelInfoText);
        scoreText = findViewById(R.id.scoreText);
        highScoreText = findViewById(R.id.highScoreText);
        restartButton = findViewById(R.id.restartButton);
        easyButton = findViewById(R.id.easyButton);
        normalButton = findViewById(R.id.normalButton);
        hardButton = findViewById(R.id.hardButton);
        levelsButton = findViewById(R.id.levelsButton);
        settingsCloseButton = findViewById(R.id.settingsCloseButton);
        settingsButton = findViewById(R.id.settingsButton);
        levelButtons = findViewById(R.id.levelButtons);
        gameOverButtons = findViewById(R.id.gameOverButtons);
        mainLayout = findViewById(R.id.main);
        raindropContainer = findViewById(R.id.raindropContainer);
        scorePanel = findViewById(R.id.scorePanel);
        gameOverPanel = findViewById(R.id.gameOverPanel);
        settingsPanel = findViewById(R.id.settingsPanel);
        // ★追加: チュートリアル関連の初期化
        tutorialPanel = findViewById(R.id.tutorialPanel);
        tutorialText = findViewById(R.id.tutorialText);
        tutorialStartButton = findViewById(R.id.tutorialStartButton);

        volumeSeekBar = findViewById(R.id.volumeSeekBar);
        vibrationSwitch = findViewById(R.id.vibrationSwitch);
        pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(v -> togglePauseGame());

        // スコアテキストに影を追加
        scoreText.setShadowLayer(3.0f, 1.0f, 1.0f, getResources().getColor(R.color.black, getTheme()));
    }

    /**
     * すべてのボタンやシークバーのイベントリスナーを設定する。
     */
    private void setupEventListeners() {
        settingsButton.setOnClickListener(v -> {
            mediaManager.playClickSound();
            showSettingsScreen();
        });

        settingsCloseButton.setOnClickListener(v -> {
            mediaManager.playClickSound();
            hideSettingsScreen();
        });

        // ★追加: チュートリアル開始ボタンのリスナー
        tutorialStartButton.setOnClickListener(v -> {
            mediaManager.playClickSound();

            // 1. チュートリアルパネルを非表示に
            tutorialPanel.setVisibility(View.GONE);

            // 2. チュートリアルを表示済みに設定（永続化）
            settingsManager.setTutorialShown(true);

            // 3. メニューBGMを開始
            mediaManager.startBGM(mediaManager.getMpMenu());

            // 4. ★追加: レベル選択画面を表示する（UIのVisibilityを制御）
            showLevelSelectScreen();

            // showLevelSelectScreen() の中で levelButtons.setVisibility(View.VISIBLE) が行われるため、
            // ユーザーはレベル選択画面に移動したことを認識できます。
        });

        easyButton.setOnClickListener(v -> {
            mediaManager.playClickSound();
//            selectedRainSpawnChance = 1;
//            selectedScoreMultiplier = 1;
            // Remote Configから最新値を取得して設定
            selectedRainSpawnChance = (int) remoteConfig.getLong("easy_spawn_chance");
            selectedScoreMultiplier = (int) remoteConfig.getLong("easy_multiplier");
            startGameFlow();
        });

        normalButton.setOnClickListener(v -> {
            mediaManager.playClickSound();
//            selectedRainSpawnChance = 5;
//            selectedScoreMultiplier = 3;
            // Remote Configから最新値を取得して設定
            selectedRainSpawnChance = (int) remoteConfig.getLong("normal_spawn_chance");
            selectedScoreMultiplier = (int) remoteConfig.getLong("normal_multiplier");
            startGameFlow();
        });

        hardButton.setOnClickListener(v -> {
            mediaManager.playClickSound();
//            selectedRainSpawnChance = 10;
//            selectedScoreMultiplier = 5;
            // Remote Configから最新値を取得して設定
            selectedRainSpawnChance = (int) remoteConfig.getLong("hard_spawn_chance");
            selectedScoreMultiplier = (int) remoteConfig.getLong("hard_multiplier");
            startGameFlow();
        });

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float volume = progress / 100.0f;
                settingsManager.setCurrentVolume(volume);
                mediaManager.adjustVolume(volume);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                settingsManager.saveSettings();
            }
        });

        vibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsManager.setVibrationOn(isChecked);
            settingsManager.saveSettings();
        });

        restartButton.setOnClickListener(v -> {
            mediaManager.playClickSound();
            startGameFlow(); // リスタートも難易度設定値で再ゲーム開始
        });

        levelsButton.setOnClickListener(v -> {
            mediaManager.playClickSound();
            showLevelSelectScreen(); // レベル選択画面に戻る
        });
    }

    /**
     * 難易度選択後のゲーム開始フローを処理する。
     */
    private void startGameFlow() {
        mediaManager.startBGM(mediaManager.getMpGame());

        // UI切り替え
        levelButtons.setVisibility(View.GONE);
        settingsButton.setVisibility(View.GONE);
        playerImage.setVisibility(View.VISIBLE);
        scorePanel.setVisibility(View.VISIBLE);
        raindropContainer.setVisibility(View.VISIBLE);
        pauseButton.setVisibility(View.VISIBLE);

        // ゲームオーバーパネル、ボタン、テキストを非表示にする
        gameOverPanel.setVisibility(View.GONE);
        gameOverButtons.setVisibility(View.GONE);
        gameOverText.setVisibility(View.GONE);
        // ★追加: チュートリアルパネルも非表示にする
        tutorialPanel.setVisibility(View.GONE);

        settingsManager.loadHighScore(); // 永続化された最新の値をSettingsManagerに読み込む
        updateHighScoreUI();             // UIに反映

        // ★★★ 状態フラグの確実なリセットと設定 ★★★
        isGamePaused = false;
        // ライフサイクルによる自動再開を防ぐフラグもリセット
        // (フィールドに追加した wasGameRunningBeforePause があればここでリセット)
        // wasGameRunningBeforePause = false;

        pauseButton.setImageResource(R.drawable.ic_pause); // アイコンを再生状態に

        // ★★★ センサーの強制的な再登録 ★★★
        // リスタート/ゲーム再開時にキャラクターが動くことを保証する
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        if (sensors.size() > 0) {
            Sensor s = sensors.get(0);
            sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // プレイヤーの初期位置設定
        playerImage.post(() -> {
            int mainWidth = mainLayout.getWidth();
            int mainHeight = mainLayout.getHeight();

            // mainLayoutの幅が有効でない場合は処理をスキップまたはデフォルト値を使用
            if (mainWidth == 0 || mainHeight == 0) {
                playerImage.setX(0);
                playerImage.setY(0);
                return;
            }

            playerImage.setX((mainWidth - playerImage.getWidth()) / 2.0f);
            playerImage.setY((mainHeight - playerImage.getHeight()) / 2.0f);

            playerImage.setImageResource(R.drawable.hangrider_woman);
        });

        // スコアとUIをリセット
        scoreText.setText("Score: 0");

        // ゲームマネージャーに処理を委譲
        gameManager.startGame(selectedRainSpawnChance, selectedScoreMultiplier);

        // Analytics - プレイ開始とレベルの取得
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.LEVEL, getLevelString()); // 選んだレベル
        params.putString("start_time", String.valueOf(System.currentTimeMillis())); // プレイ開始時間
        mFirebaseAnalytics.logEvent("game_start", params);
    }

    /**
     * 設定画面を表示する。
     */
    private void showSettingsScreen() {
        levelButtons.setVisibility(View.GONE);
        playerImage.setVisibility(View.GONE);
        scorePanel.setVisibility(View.GONE);
        settingsButton.setVisibility(View.GONE);
        pauseButton.setVisibility(View.GONE);
        // ★追加: チュートリアルパネルを非表示にする
        tutorialPanel.setVisibility(View.GONE);

        // 設定値をUIに反映
        volumeSeekBar.setProgress((int)(settingsManager.getCurrentVolume() * 100));
        vibrationSwitch.setChecked(settingsManager.isVibrationOn());

        settingsPanel.setVisibility(View.VISIBLE);
    }

    /**
     * 設定画面を非表示にし、スタート画面（難易度選択）に戻る。
     */
    private void hideSettingsScreen() {
        settingsPanel.setVisibility(View.GONE);
        levelButtons.setVisibility(View.VISIBLE);
        settingsButton.setVisibility(View.VISIBLE);
        mediaManager.startBGM(mediaManager.getMpMenu());
    }

    // ★追加: チュートリアル画面を表示するメソッド
    /**
     * チュートリアル画面を表示する。
     */
    private void showTutorial() {
        // 他のUI要素を全て非表示にする（ゲーム画面、設定画面など）
        levelButtons.setVisibility(View.GONE);
        settingsButton.setVisibility(View.GONE);
        playerImage.setVisibility(View.GONE);
        scorePanel.setVisibility(View.GONE);
        raindropContainer.setVisibility(View.GONE);
        gameOverPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        pauseButton.setVisibility(View.GONE);

        tutorialPanel.setVisibility(View.VISIBLE); // チュートリアルパネルを表示

        // 必要であれば、ここでチュートリアル用のBGMを再生
        // mediaManager.startBGM(mediaManager.getMpTutorial());
    }


    /**
     * 難易度選択画面に戻る。
     */
    private void showLevelSelectScreen() {
        mediaManager.startBGM(mediaManager.getMpMenu());

        // UIリセット
        scoreText.setText("Score: 0");
        gameOverText.setVisibility(View.GONE);
        gameOverButtons.setVisibility(View.GONE);

        // ★最重要修正: 画面を覆うパネルを確実に非表示にする
        gameOverPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        tutorialPanel.setVisibility(View.GONE);

        raindropContainer.removeAllViews();
        playerImage.setVisibility(View.GONE);
        scorePanel.setVisibility(View.GONE);
        raindropContainer.setVisibility(View.GONE);

        // ★レベル選択画面の表示に必要な要素を可視化
        levelButtons.setVisibility(View.VISIBLE);
        settingsButton.setVisibility(View.VISIBLE); // 設定ボタンはここで再表示
        pauseButton.setVisibility(View.GONE);

        updateHighScoreUI();
    }

    /**
     * ハイスコアUIを更新する。
     */
    private void updateHighScoreUI() {
        highScoreText.setText("High Score: " + settingsManager.getHighScore());
    }

    // =========================================================================================
    // GameManager.GameCallback の実装 (GameManagerからのコールバック)
    // =========================================================================================

    /**
     * 雨粒を避けてスコアが加算されたときに呼ばれる。
     * @param addedScore 獲得したスコア（倍率適用済み）
     */
    @Override
    public void onRaindropMissed(int addedScore) {
        scoreText.setText("Score: " + gameManager.getScore());
        startScoreAnimation(addedScore);
    }

    // 一時停止/再開を切り替えるメソッド
    private void togglePauseGame() {
        wasGameRunningBeforePause = false;
        if (isGamePaused) {
            // ゲームを再開
            gameManager.resumeGame();
            mediaManager.startBGM(mediaManager.getMpGame());
            pauseButton.setImageResource(R.drawable.ic_pause);
            isGamePaused = false;
            // ★追加: センサーを再開
            List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
            if (sensors.size() > 0) {
                Sensor s = sensors.get(0);
                sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else {
            // ゲームを一時停止
            gameManager.pauseGame();
            mediaManager.pauseBGM();
            pauseButton.setImageResource(R.drawable.ic_play);
            isGamePaused = true;
            // ★追加: センサーを停止
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
        }
    }

    /**
     * 衝突が発生し、ゲームオーバーになったときに呼ばれる。
     * @param score 最終スコア
     */
    @Override
    public void onGameOver(int score) {
        mediaManager.playHitSound();

        // ★デバッグコードを追加
        boolean isVibEnabled = settingsManager.isVibrationOn();
        boolean hasVibrator = vibrator != null && vibrator.hasVibrator();

        // Logcatで確認するためのログ出力
        android.util.Log.d("VIBE_DEBUG", "Vibration Setting ON: " + isVibEnabled);
        android.util.Log.d("VIBE_DEBUG", "Device Has Vibrator: " + hasVibrator);

        // 振動処理
        if (isVibEnabled && hasVibrator) {
            android.util.Log.d("VIBE_DEBUG", "VIBRATION REQUEST SENT!"); // ★リクエスト送信のログ
            vibrator.vibrate(150);
        } else {
            android.util.Log.d("VIBE_DEBUG", "Vibration skipped (Setting OFF or No hardware).");
        }

        mediaManager.startBGM(mediaManager.getMpGameOver());

        settingsManager.saveHighScore(score);

        // Analytics - プレイ終了、スコア、プレイ時間、終了時刻の取得
        long endTime = System.currentTimeMillis();
        long startTime = gameManager.getGameStartTime();
        long durationSeconds = (endTime - startTime) / 1000;

        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.LEVEL, getLevelString());   // 選んだレベル
        params.putLong(FirebaseAnalytics.Param.SCORE, score);                // 最終スコア
        params.putLong(FirebaseAnalytics.Param.VALUE, durationSeconds);      // プレイ時間 (秒)
        params.putString("end_time", String.valueOf(endTime));               // プレイ終了時間

        // LEVEL_END (プレイ終了) イベントとして記録
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LEVEL_END, params);

        playerImage.setImageResource(R.drawable.hit_effect);

        scoreAnimHandler.postDelayed(this::showGameOverScreenUI, 500);

        pauseButton.setVisibility(View.GONE);
        isGamePaused = false; // ゲームオーバー時は一時停止状態ではない
        pauseButton.setImageResource(R.drawable.ic_pause); // アイコンをリセット
//        settingsButton.setVisibility(View.VISIBLE); // 設定ボタンを再表示
    }

    // ★追加: Analytics用のヘルパーメソッド
    private String getLevelString() {
        // Remote Configの値ではなく、対応するSpawnChanceの値で判断する
        if (selectedRainSpawnChance == remoteConfig.getLong("easy_spawn_chance")) return "easy";
        if (selectedRainSpawnChance == remoteConfig.getLong("normal_spawn_chance")) return "normal";
        if (selectedRainSpawnChance == remoteConfig.getLong("hard_spawn_chance")) return "hard";
        return "unknown";
    }

    private void showGameOverScreenUI() {
        playerImage.setVisibility(View.GONE);
        gameOverText.setVisibility(View.VISIBLE);
        gameOverButtons.setVisibility(View.VISIBLE);
        gameOverPanel.setVisibility(View.VISIBLE);

        pauseButton.setVisibility(View.GONE);
        // ゲームオーバー時に設定ボタンを非表示
        settingsButton.setVisibility(View.GONE);

        String gameOverMsg = "GAME OVER<br><br>"
                + "<small>Score: " + gameManager.getScore() + "</small><br>"
                + "<small>High Score: " + settingsManager.getHighScore() + "</small>";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            gameOverText.setText(Html.fromHtml(gameOverMsg, Html.FROM_HTML_MODE_LEGACY));
        } else {
            gameOverText.setText(Html.fromHtml(gameOverMsg));
        }

        // levelButtons (難易度選択パネル) はここで GONE に保たれていることを確認
        levelButtons.setVisibility(View.GONE);
    }

    /**
     * レベルアップが発生したときに呼ばれる。
     * @param currentLevel 現在のレベル
     */
    @Override
    public void onLevelUp(int currentLevel) {
        mediaManager.playLevelUpSound();
        runFlashEffect();

        // LEVEL UP! テキスト表示
        levelInfoText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24);
        levelInfoText.setTextColor(getResources().getColor(R.color.colorAccentScore, getTheme()));
        levelInfoText.setText("LEVEL UP! " + currentLevel);

        // 1秒後に元の表示に戻す（次のコールバックで上書きされるため、UIの装飾のみ解除）
        scoreAnimHandler.postDelayed(() -> {
            levelInfoText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            levelInfoText.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
        }, 1000);
    }

    /**
     * レベル情報テキスト（カウントダウン）を更新する。
     * @param text 表示するテキスト
     * @param textSize テキストサイズ (sp)
     * @param textColor テキストの色 (リソースID)
     * @param alpha 透明度
     */
    @Override
    public void onUpdateLevelText(String text, int textSize, int textColor, float alpha) {
        levelInfoText.setText(text);
        if (textColor != 0) { // 色が指定されている場合のみ適用
            levelInfoText.setTextColor(textColor);
        }
        levelInfoText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize);
        levelInfoText.setAlpha(alpha);
    }

    // =========================================================================================
    // UI/アニメーション/センサー
    // =========================================================================================

    /**
     * スコアが加算されたときのアニメーション（スコア表示拡大とフローティングテキスト）を実行する。
     * @param addedScore 獲得したスコア（倍率適用済み）
     */
    private void startScoreAnimation(int addedScore) {
        // 1. スコアTextViewのアニメーション
        scoreText.animate()
                .scaleX(SCORE_SCALE_FACTOR)
                .scaleY(SCORE_SCALE_FACTOR)
                .setDuration(SCORE_ANIM_DURATION)
                .withStartAction(() -> scoreText.setTextColor(getResources().getColor(R.color.colorAccentScore, getTheme())))
                .withEndAction(() -> {
                    scoreText.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(SCORE_ANIM_DURATION)
                            .start();
                    scoreText.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                })
                .start();

        // 2. フローティングテキスト (+Xpt) の作成とアニメーション
        TextView floatText = new TextView(this);
        floatText.setText("+" + addedScore);
        floatText.setTextColor(getResources().getColor(R.color.colorAccentScore, getTheme()));
        floatText.setTextSize(20);
        floatText.setElevation(5);
        floatText.setShadowLayer(3.0f, 1.0f, 1.0f, getResources().getColor(R.color.black, getTheme()));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        float startX = playerImage.getX() + (playerImage.getWidth() / 2.0f);
        float startY = playerImage.getY() - 50;

        floatText.setX(startX);
        floatText.setY(startY);
        mainLayout.addView(floatText, params);

        // 上に移動しつつ、フェードアウトするアニメーション
        floatText.animate()
                .translationYBy(-100)
                .alpha(0.0f)
                .setDuration(800)
                .withEndAction(() -> mainLayout.removeView(floatText))
                .start();
    }

    /**
     * 画面全体を短時間フラッシュさせる演出。
     */
    private void runFlashEffect() {
        View flashView = new View(this);
        flashView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        flashView.setBackgroundColor(getResources().getColor(android.R.color.white, getTheme()));
        flashView.setAlpha(0.0f);
        flashView.setElevation(100f);

        mainLayout.addView(flashView);

        // フェードイン (一瞬明るく)
        flashView.animate()
                .alpha(0.8f)
                .setDuration(100)
                .withEndAction(() -> {
                    // フェードアウト
                    flashView.animate()
                            .alpha(0.0f)
                            .setDuration(300)
                            .withEndAction(() -> mainLayout.removeView(flashView))
                            .start();
                })
                .start();
    }

    // =========================================================================================
    // センサーイベントリスナー
    // =========================================================================================

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * センサー値が変化したときにプレイヤー画像を移動・回転させる。
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // ゲームオーバー時には操作を受け付けない
        // ★修正: チュートリアル表示中も操作を受け付けない
        if (gameOverPanel.getVisibility() == View.VISIBLE || levelButtons.getVisibility() == View.VISIBLE || tutorialPanel.getVisibility() == View.VISIBLE) return;

        // ★追加: ゲームが一時停止中の場合は操作を受け付けない
        if (isGamePaused) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];

            // 1. キャラクターの水平移動
            float newX = playerImage.getX() - (x * PLAYER_SPEED_FACTOR);
            int screenWidth = mainLayout.getWidth();
            if (newX < 0) newX = 0;
            if (newX + playerImage.getWidth() > screenWidth) {
                newX = screenWidth - playerImage.getWidth();
            }
            playerImage.setX(newX);

            // 2. キャラクターの垂直移動 (y軸は前後傾きで、ここでは上下移動に使用)
            // ★重要: このロジックは、以前の会話で「意図しない垂直移動」として削除が推奨されたものです。
            // 　　　　Gitプッシュ前にロジックを整理する場合は、このブロック全体を削除してください。
            float y = event.values[1];
            float newY = playerImage.getY() + (y * PLAYER_SPEED_FACTOR);
            int screenHeight = mainLayout.getHeight();
            if (newY < 0) newY = 0;
            if (newY + playerImage.getHeight() > screenHeight) {
                newY = screenHeight - playerImage.getHeight();
            }
            playerImage.setY(newY);
            // ★重要: 垂直移動ロジックの終わり

            // 3. キャラクターの回転
            final float MAX_ROTATION_DEGREE = 20.0f;
            final float SENSOR_SENSITIVITY = 4.0f;

            float rotationDegree = -(x / SENSOR_SENSITIVITY) * MAX_ROTATION_DEGREE;
            if (rotationDegree > MAX_ROTATION_DEGREE) {
                rotationDegree = MAX_ROTATION_DEGREE;
            } else if (rotationDegree < -MAX_ROTATION_DEGREE) {
                rotationDegree = -MAX_ROTATION_DEGREE;
            }

            playerImage.setRotation(rotationDegree);
        }
    }
}