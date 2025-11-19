package com.example.acc_02;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * ゲームロジックの中核を担うクラス。
 * ゲームループ、雨粒の生成/移動、衝突判定、難易度調整を行う。
 */
public class GameManager {

    // ゲーム開始時のシステム時刻を保持
    private long gameStartTime;
    private boolean isPaused = false;
    private boolean isRunning = false; // ★追加: ゲームが実行中かどうか

    // --- インターフェース ---
    public interface GameCallback {
        void onRaindropMissed(int addedScore);
        void onGameOver(int score);
        void onUpdateLevelText(String text, int textSize, int textColor, float alpha);
        void onLevelUp(int currentLevel);
    }

    private final Context context;
    private final FrameLayout raindropContainer;
    private final ViewGroup mainLayout;
    private final ImageView playerImage;
    private final GameCallback callback;

    // --- ゲーム状態変数 ---
    private boolean isGameOver = false;
    private int score = 0; // スコア変数はここに移動
    private int scoreMultiplier = 1;
    private final ArrayList<ImageView> raindrops = new ArrayList<>();
    private final Random random = new Random();

    // --- ゲームループ/ドロップ関連 ---
    private final Handler gameHandler;
    private final Runnable gameRunnable; // ★修正: 無名クラスの定義を削除し、Runnableオブジェクトとして宣言

    // --- レベル/時間管理 ---
    private long startTime;
    private int currentLevel = 1;
    private static final int LEVEL_UP_INTERVAL = 15000; // 15秒

    // 一時停止/再開の時間管理用
    private long pauseTime = 0; // 一時停止した時刻

    // --- 難易度設定 ---
    private int baseRainSpawnChance = 5; // 難易度選択で設定される初期値
    private float currentRainSpeed = 20.0f;
    private int currentRainSpawnChance = 5;

    public GameManager(Context context, FrameLayout raindropContainer, ViewGroup mainLayout, ImageView playerImage, GameCallback callback) {
        this.context = context;
        this.raindropContainer = raindropContainer;
        this.mainLayout = mainLayout;
        this.playerImage = playerImage;
        this.callback = callback;

        this.gameHandler = new Handler(Looper.getMainLooper());
        this.gameRunnable = this::runGameLoop; // ★修正: runGameLoop メソッドをRunnableとして設定
    }

    /**
     * ゲームを開始し、状態変数を初期化する。
     * @param initialSpawnChance 選択された難易度に基づく初期の雨の発生確率
     * @param multiplier スコア倍率
     */
    public void startGame(int initialSpawnChance, int multiplier) {
        this.scoreMultiplier = multiplier;
        this.baseRainSpawnChance = initialSpawnChance;

        isGameOver = false;
        score = 0;
        isPaused = false;   // ★修正: ゲーム開始時は一時停止状態ではない
        isRunning = true;   // ★追加: ゲーム開始時は実行中

        // レベル/時間設定をリセット
        currentRainSpeed = 20.0f;
        currentRainSpawnChance = baseRainSpawnChance;
        currentLevel = 1;
        startTime = System.currentTimeMillis();
        gameStartTime = System.currentTimeMillis(); // ★追加: ゲーム開始時刻を記録

        raindropContainer.removeAllViews();
        raindrops.clear();

        // levelInfoTextの表示をリセットさせるコールバック
        callback.onUpdateLevelText("", 14, 0, 1.0f); // 初期表示クリア

        gameHandler.post(gameRunnable); // ★ startGameLoop() を呼び出す代わりに直接post

        // 以前の startGameLoop() は重複するため削除
    }

    /**
     * ゲームループを再開する（onPauseからの復帰などに使用）。
     * MainActivityのonResumeから呼ばれることを想定。
     */
    public void startGameLoop() {
        // ★修正: isGameOver または isPaused の場合は再開しない。
        // isRunning のチェックは、重複ポストを防ぐためにも維持する。
        if (!isGameOver && !isPaused && !isRunning) { // ★ isPaused のチェックを追加
            isRunning = true;
            gameHandler.post(gameRunnable);
        }
        // isPaused が true の場合は、togglePauseGame() から呼ばれる resumeGame() で再開されるため、
        // ここでは何もしない。（isRunning = true にしないことで、resumeGame() の再開条件を維持する）
    }

    /**
     * ゲームループを停止する。
     */
    public void stopGameLoop() {
        isRunning = false;      // ★修正: 実行中ではない
        isPaused = false;       // ★修正: 停止時は一時停止状態を解除
        gameHandler.removeCallbacks(gameRunnable);
        // 必要であれば、雨粒などもすべて削除
        raindropContainer.removeAllViews();
        raindrops.clear();
    }

    /**
     * 現在のスコアを取得する。
     */
    public int getScore() {
        return score;
    }

    /**
     * ゲームループで実行される主要な処理（雨生成、移動、衝突判定）。
     */
//    private void runGameLoop() {
//        if (!isGameOver && !isPaused) { // ★修正: isPaused で一時停止をチェック
//            updateLevelTimer();
//            spawnRaindrop();
//            moveRaindrops();
//            checkCollisions();
//            gameHandler.removeCallbacks(gameRunnable);
//            gameHandler.postDelayed(gameRunnable, 16); // 約60FPS
//        } else if (isPaused) {
//            gameHandler.removeCallbacks(gameRunnable);
//            gameHandler.postDelayed(gameRunnable, 16);
//        }
//
//        // isGameOverの場合は、postDelayedしないので自然にループが止まる
//    }

    private void runGameLoop() {
        if (isGameOver || isPaused) {
            gameHandler.removeCallbacks(gameRunnable);
            return;
        }

        // 以前の runGameLoop() のメインロジック...
        updateLevelTimer();
        spawnRaindrop();
        moveRaindrops();
        checkCollisions();

        // 次のフレームをスケジュール
        gameHandler.postDelayed(gameRunnable, 16); // 約60FPS
    }

    /**
     * ライフサイクル用：ゲームループを一時停止する（コールバックを削除するのみ）。
     * MainActivityのonPauseから呼ばれることを想定。
     */
    public void pauseLoopOnly() {
        isRunning = false;
        // isPaused はここでは変更しない（MainActivityのフラグに依存）。
        gameHandler.removeCallbacks(gameRunnable);
    }

    /**
     * ゲームを完全に終了する（ゲームオーバー時、リスタート前など）。
     * 既存の stopGameLoop() をこれに置き換えます。
     */
    public void fullStop() {
        isRunning = false;
        isPaused = false;
        gameHandler.removeCallbacks(gameRunnable);

        // ★重要: 雨粒もすべて削除する (ゲームオーバー処理)
        raindropContainer.removeAllViews();
        raindrops.clear();
    }

    /**
     * レベルアップまでのカウントダウンを更新し、レベルアップ処理を行う。
     */
    private void updateLevelTimer() {
        long elapsedTime = System.currentTimeMillis() - startTime;
        int expectedLevel = (int) (elapsedTime / LEVEL_UP_INTERVAL) + 1;

        // レベルアップ判定
        if (expectedLevel > currentLevel) {
            currentLevel = expectedLevel;
            increaseDifficulty();
            callback.onLevelUp(currentLevel);
        }

        // カウントダウンの計算と表示
        long timeToNextLevelMs = LEVEL_UP_INTERVAL - (elapsedTime % LEVEL_UP_INTERVAL);
        int timeToNextLevelSec = (int) Math.ceil(timeToNextLevelMs / 1000.0);

        if (timeToNextLevelSec > 0) {
            String text = "NEXT LEVEL: " + timeToNextLevelSec + "s";
            // ★色とアルファ値の計算はMainActivityに委譲せず、GameManagerがデータを提供する形に修正
            int color = context.getResources().getColor(android.R.color.white, context.getTheme());
            float alpha = 1.0f;

            // 残り5秒の点滅演出
            if (timeToNextLevelSec <= 5) {
                color = context.getResources().getColor(android.R.color.holo_red_light, context.getTheme());
                alpha = timeToNextLevelSec % 2 == 0 ? 0.3f : 1.0f;
            }
            callback.onUpdateLevelText(text, 14, color, alpha);
        }
    }

    /**
     * 難易度レベルを上げ、雨粒の速度と発生頻度を増加させる。
     */
    private void increaseDifficulty() {
        // 難易度パラメータ更新
        currentRainSpeed += 1.0f;
        if (currentRainSpawnChance < 30) {
            currentRainSpawnChance += 1;
        }
    }

    /**
     * 一定の確率で画面上部に雨粒を生成する。
     */
    private void spawnRaindrop() {
        if (random.nextInt(100) < currentRainSpawnChance) {
            ImageView raindrop = new ImageView(context);
            raindrop.setImageResource(R.drawable.ame);
            raindrop.setLayoutParams(new ViewGroup.LayoutParams(50, 50));
            raindropContainer.addView(raindrop);

            raindropContainer.post(() -> {
                int screenWidth = raindropContainer.getWidth();
                raindrop.setX(random.nextInt(screenWidth - 50));
                raindrop.setY(0);
            });

            raindrops.add(raindrop);
        }
    }

    /**
     * 生成されたすべての雨粒を下に移動させ、画面外に出たものを削除する。
     */
    private void moveRaindrops() {
        Iterator<ImageView> iterator = raindrops.iterator();
        while (iterator.hasNext()) {
            ImageView raindrop = iterator.next();
            raindrop.setY(raindrop.getY() + currentRainSpeed);

            if (raindrop.getY() > mainLayout.getHeight()) {
                raindropContainer.removeView(raindrop);
                iterator.remove();

                final int addedScore = 1;
                score += addedScore * scoreMultiplier;
                callback.onRaindropMissed(addedScore * scoreMultiplier); // スコア通知
            }
        }
    }

    /**
     * プレイヤーと雨粒の衝突判定を行い、衝突した場合にゲームオーバー処理を行う。
     */
    private void checkCollisions() {
        if (isGameOver) return;

        float playerX = playerImage.getX();
        float playerY = playerImage.getY();
        float playerWidth = playerImage.getWidth();
        float playerHeight = playerImage.getHeight();

        final float COLLISION_FACTOR = 0.6f;
        float collisionWidth = playerWidth * COLLISION_FACTOR;
        float collisionHeight = playerHeight * COLLISION_FACTOR;
        float collisionX = playerX + (playerWidth - collisionWidth) / 2.0f;
        float collisionY = playerY + (playerHeight - collisionHeight) / 2.0f;

        ImageView collidedRaindrop = null;
        for (ImageView raindrop : raindrops) {
            float rainX = raindrop.getX();
            float rainY = raindrop.getY();
            float rainWidth = raindrop.getWidth();
            float rainHeight = raindrop.getHeight();

            if (collisionX < rainX + rainWidth &&
                    collisionX + collisionWidth > rainX &&
                    collisionY < rainY + rainHeight &&
                    collisionY + collisionHeight > rainY) {

                collidedRaindrop = raindrop;
                break;
            }
        }

        if (collidedRaindrop != null) {
            isGameOver = true;
            // ★修正: ゲームオーバーなので fullStop を呼ぶ
            fullStop();

            raindropContainer.removeView(collidedRaindrop);
            raindrops.remove(collidedRaindrop);

            callback.onGameOver(score);
        }
    }

    /**
     * ゲーム開始時のシステム時刻を取得する (Analytics用)
     */
    public long getGameStartTime() {
        return gameStartTime;
    }

    /**
     * ゲームを一時停止する
     */
    public void pauseGame() {
        if (isRunning && !isPaused) {
            isPaused = true;
            pauseTime = System.currentTimeMillis();
            gameHandler.removeCallbacks(gameRunnable); // 明示的にループを停止
        }
    }

    /**
     * ゲームを再開する
     */
    public void resumeGame() {
        // if (isRunning && isPaused) { // <-- 変更前
        if (isPaused) { // ★修正: isRunningのチェックを削除
            isPaused = false;

            // タイマー調整ロジックを追加
            if (pauseTime > 0) {
                long elapsedTime = System.currentTimeMillis() - pauseTime;
                startTime += elapsedTime; // 基準時刻を「遅らせる」ことで、一時停止時間を無視する
                pauseTime = 0;
            }

            // isRunning = true に設定する必要があるか？
            // -> gameHandler.post(gameRunnable); でループが再開されれば、
            //    startGameLoop() の if 文に引っかからなくなるため、不要か、
            //    あるいは明示的に true に設定しても良い。
            isRunning = true; // ★追加: 明示的に true に設定することで、状態を一致させる

            gameHandler.removeCallbacks(gameRunnable);  //重複を防ぐ
            gameHandler.post(gameRunnable); // ゲームループを再開
        }
    }
}