package catalinc.games.pong;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import java.util.Random;

/**
 * Handle animation, game logic and user input.
 */
public class PongThread extends Thread {

    /*
     * State related constants
     */
    public static final int STATE_PAUSE = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_LOSE = 3;
    public static final int STATE_WIN = 4;

    /*
     * Physics constants
     */
    private static final int PHYS_BALL_SPEED = 8;
    private static final int PHYS_PADDLE_SPEED = 12;
    private static final int PHYS_FPS = 60;
    private static final double PHYS_MAX_BOUNCE_ANGLE = 5 * Math.PI / 12; // 75 degrees in radians

    /*
    * Constants used when game state is saved/restored
    */
    private static final String KEY_HUMAN_PLAYER_DATA = "humanPlayer";
    private static final String KEY_COMPUTER_PLAYER_DATA = "computerPlayer";
    private static final String KEY_BALL_DATA = "ball";

    private static final String TAG = "PongThread";

    /**
     * Handle to the surface manager object we interact with
     */
    private final SurfaceHolder mSurfaceHolder;

    /**
     * Message handler used by thread to interact with status TextView
     */
    private final Handler mStatusHandler;

    /**
     * Message handler used by thread to interact with score TextView
     */
    private final Handler mScoreHandler;

    /**
     * Handle to the application context
     */
    private final Context mContext;

    /**
     * Indicate whether the surface has been created & is ready to draw
     */
    private boolean mRun = false;

    /**
     * The state of the game.
     */
    private int mState;

    /*
     * Game objects
     */
    private Player mHumanPlayer;
    private Player mComputerPlayer;
    private Ball mBall;

    /**
     * Median line paint style.
     */
    private Paint mMedianLinePaint;

    /**
     * Canvas bounds paint.
     */
    private Paint mCanvasBoundsPaint;

    /**
     * Current height of the canvas.
     */
    private int mCanvasHeight = 1;

    /**
     * Current width of the canvas.
     */
    private int mCanvasWidth = 1;

    /**
     * Used to make computer to "forget" to move the paddle in order to behave more like a human opponent.
     */
    private Random mRandomGen;

    /**
     * The probability to move computer paddle.
     */
    private float mComputerMoveProbability;


    PongThread(final SurfaceHolder surfaceHolder,
               final Context context,
               final Handler statusHandler,
               final Handler scoreHandler,
               final AttributeSet attributeSet) {
        mSurfaceHolder = surfaceHolder;
        mStatusHandler = statusHandler;
        mScoreHandler = scoreHandler;
        mContext = context;

        TypedArray a = context.obtainStyledAttributes(attributeSet, R.styleable.PongView);

        int paddleHeight = a.getInt(R.styleable.PongView_paddleHeight, 85);
        int paddleWidth = a.getInt(R.styleable.PongView_paddleWidth, 25);
        int ballRadius = a.getInt(R.styleable.PongView_ballRadius, 15);

        a.recycle();

        Paint humanPlayerPaint = new Paint();
        humanPlayerPaint.setAntiAlias(true);
        humanPlayerPaint.setColor(Color.BLUE);

        mHumanPlayer = new Player(paddleWidth, paddleHeight, humanPlayerPaint);

        Paint computerPlayerPaint = new Paint();
        computerPlayerPaint.setAntiAlias(true);
        computerPlayerPaint.setColor(Color.RED);

        mComputerPlayer = new Player(paddleWidth, paddleHeight, computerPlayerPaint);

        Paint ballPaint = new Paint();
        ballPaint.setAntiAlias(true);
        ballPaint.setColor(Color.GREEN);

        mBall = new Ball(ballRadius, ballPaint);

        mMedianLinePaint = new Paint();
        mMedianLinePaint.setAntiAlias(true);
        mMedianLinePaint.setColor(Color.YELLOW);
        mMedianLinePaint.setAlpha(80);
        mMedianLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mMedianLinePaint.setStrokeWidth(2.0f);
        mMedianLinePaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));

        mCanvasBoundsPaint = new Paint();
        mCanvasBoundsPaint.setAntiAlias(true);
        mCanvasBoundsPaint.setColor(Color.YELLOW);
        mCanvasBoundsPaint.setStyle(Paint.Style.STROKE);
        mCanvasBoundsPaint.setStrokeWidth(1.0f);

        mRandomGen = new Random();
        mComputerMoveProbability = 0.6f;
    }

    /**
     * Game loop.
     */
    @Override
    public void run() {
        long mNextGameTick = SystemClock.uptimeMillis();
        int skipTicks = 1000 / PHYS_FPS;
        while (mRun) {
            Canvas c = null;
            try {
                c = mSurfaceHolder.lockCanvas(null);
                if (c != null) {
                    synchronized (mSurfaceHolder) {
                        if (mState == STATE_RUNNING) {
                            updatePhysics();
                        }
                        updateDisplay(c);
                    }
                }
            } finally {
                if (c != null) {
                    mSurfaceHolder.unlockCanvasAndPost(c);
                }
            }
            mNextGameTick += skipTicks;
            long sleepTime = mNextGameTick - SystemClock.uptimeMillis();
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted", e);
                }
            }
        }
    }

    /**
     * Used to signal game thread whether it should be running or not.
     *
     * @param running true to run, false to shut down
     */
    void setRunning(boolean running) {
        mRun = running;
    }

    /**
     * Save game state to the provided Bundle.
     *
     * @param map bundle to save game state
     */
    void saveState(Bundle map) {
        synchronized (mSurfaceHolder) {
            map.putFloatArray(KEY_HUMAN_PLAYER_DATA,
                    new float[]{mHumanPlayer.bounds.left,
                            mHumanPlayer.bounds.top,
                            mHumanPlayer.score});

            map.putFloatArray(KEY_COMPUTER_PLAYER_DATA,
                    new float[]{mComputerPlayer.bounds.left,
                            mComputerPlayer.bounds.top,
                            mComputerPlayer.score});

            map.putFloatArray(KEY_BALL_DATA,
                    new float[]{mBall.cx, mBall.cy, mBall.dx, mBall.dy});
        }
    }

    /**
     * Restores game state from specified bundle.
     *
     * @param map bundle containing the game state
     */
    void restoreState(Bundle map) {
        synchronized (mSurfaceHolder) {
            float[] humanPlayerData = map.getFloatArray(KEY_HUMAN_PLAYER_DATA);
            mHumanPlayer.score = (int) humanPlayerData[2];
            movePlayer(mHumanPlayer, humanPlayerData[0], humanPlayerData[1]);

            float[] computerPlayerData = map.getFloatArray(KEY_COMPUTER_PLAYER_DATA);
            mComputerPlayer.score = (int) computerPlayerData[2];
            movePlayer(mComputerPlayer, computerPlayerData[0], computerPlayerData[1]);

            float[] ballData = map.getFloatArray(KEY_BALL_DATA);
            mBall.cx = ballData[0];
            mBall.cy = ballData[1];
            mBall.dx = ballData[2];
            mBall.dy = ballData[3];
        }
    }

    /**
     * Sets the game mode.
     *
     * @param mode one of the state constants
     */
    void setState(int mode) {
        synchronized (mSurfaceHolder) {
            mState = mode;
            Resources res = mContext.getResources();
            switch (mState) {
                case STATE_READY:
                    setupNewRound();
                    break;
                case STATE_RUNNING:
                    hideStatusText();
                    break;
                case STATE_WIN:
                    setStatusText(res.getString(R.string.mode_win));
                    mHumanPlayer.score++;
                    setupNewRound();
                    break;
                case STATE_LOSE:
                    setStatusText(res.getString(R.string.mode_lose));
                    mComputerPlayer.score++;
                    setupNewRound();
                    break;
                case STATE_PAUSE:
                    setStatusText(res.getString(R.string.mode_pause));
                    break;
            }
        }
    }

    /**
     * Pauses the animation.
     */
    void pause() {
        synchronized (mSurfaceHolder) {
            if (mState == STATE_RUNNING) {
                setState(STATE_PAUSE);
            }
        }
    }

    /**
     * Resumes from a pause.
     */
    void unPause() {
        synchronized (mSurfaceHolder) {
            setState(STATE_RUNNING);
        }
    }

    /**
     * Reset score and start new game.
     */
    void startNewGame() {
        synchronized (mSurfaceHolder) {
            mHumanPlayer.score = 0;
            mComputerPlayer.score = 0;
            setupNewRound();
            setState(STATE_RUNNING);
        }
    }

    /**
     * @return true if the game is in win, lose or pause state.
     */
    boolean isBetweenRounds() {
        return mState != STATE_RUNNING;
    }

    /**
     * Returns true if the touch event is on Player A paddle bounds.
     *
     * @param event touch even
     * @return true if touch is on Player A paddle
     */
    boolean isTouchOnHumanPaddle(MotionEvent event) {
        return mHumanPlayer.bounds.contains(event.getX(), event.getY());
    }

    /**
     * Move Player A paddle according to touch event.
     *
     * @param event touch move event
     */
    void handleMoveHumanPaddleEvent(MotionEvent event) {
        synchronized (mSurfaceHolder) {
            movePlayer(mHumanPlayer,
                    mHumanPlayer.bounds.left,
                    event.getY() - mHumanPlayer.paddleHeight / 2);
        }
    }

    /**
     * Callback invoked when the surface dimensions change.
     *
     * @param width  canvas width
     * @param height canvas height
     */
    void setSurfaceSize(int width, int height) {
        synchronized (mSurfaceHolder) {
            mCanvasWidth = width;
            mCanvasHeight = height;
            setupNewRound();
        }
    }

    /**
     * Update paddle and player positions, check for collisions, win or lose.
     */
    private void updatePhysics() {

        mHumanPlayer.isHit = false;
        mComputerPlayer.isHit = false;

        if (collision(mHumanPlayer, mBall)) {
            handleCollision(mHumanPlayer, mBall);
            mHumanPlayer.isHit = true;
        } else if (collision(mComputerPlayer, mBall)) {
            handleCollision(mComputerPlayer, mBall);
            mComputerPlayer.isHit = true;
        } else if (ballCollidedWithTopOrBottomWall()) {
            mBall.dy = -mBall.dy;
        } else if (ballCollidedWithRightWall()) {
            setState(STATE_WIN);    // human plays on left
            return;
        } else if (ballCollidedWithLeftWall()) {
            setState(STATE_LOSE);
            return;
        }

        if (mRandomGen.nextFloat() < mComputerMoveProbability) {
            doAI();
        }

        moveBall();
    }

    private void moveBall() {
        mBall.cx += mBall.dx;
        mBall.cy += mBall.dy;

        if (mBall.cy < mBall.radius) {
            mBall.cy = mBall.radius;
        } else if (mBall.cy + mBall.radius >= mCanvasHeight) {
            mBall.cy = mCanvasHeight - mBall.radius - 1;
        }
    }

    /**
     * Move the computer paddle to hit the ball.
     */
    private void doAI() {
        if (mComputerPlayer.bounds.top > mBall.cy) {
            // move up
            movePlayer(mComputerPlayer,
                    mComputerPlayer.bounds.left,
                    mComputerPlayer.bounds.top - PHYS_PADDLE_SPEED);
        } else if (mComputerPlayer.bounds.top + mComputerPlayer.paddleHeight < mBall.cy) {
            // move down
            movePlayer(mComputerPlayer,
                    mComputerPlayer.bounds.left,
                    mComputerPlayer.bounds.top + PHYS_PADDLE_SPEED);
        }
    }

    private boolean ballCollidedWithLeftWall() {
        return mBall.cx <= mBall.radius;
    }

    private boolean ballCollidedWithRightWall() {
        return mBall.cx + mBall.radius >= mCanvasWidth - 1;
    }

    private boolean ballCollidedWithTopOrBottomWall() {
        return mBall.cy <= mBall.radius
                || mBall.cy + mBall.radius >= mCanvasHeight - 1;
    }

    /**
     * Draws the score, paddles and the ball.
     */
    private void updateDisplay(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        canvas.drawRect(0, 0, mCanvasWidth, mCanvasHeight, mCanvasBoundsPaint);

        final int middle = mCanvasWidth / 2;
        canvas.drawLine(middle, 1, middle, mCanvasHeight - 1, mMedianLinePaint);

        setScoreText(mHumanPlayer.score + "    " + mComputerPlayer.score);

        handleHit(mHumanPlayer);
        handleHit(mComputerPlayer);

        canvas.drawRoundRect(mHumanPlayer.bounds, 5, 5, mHumanPlayer.paint);
        canvas.drawRoundRect(mComputerPlayer.bounds, 5, 5, mComputerPlayer.paint);
        canvas.drawCircle(mBall.cx, mBall.cy, mBall.radius, mBall.paint);
    }

    private void handleHit(Player player) {
        if (player.isHit) {
           player.paint.setShadowLayer(10, 0, 0, Color.YELLOW);
        } else {
            player.paint.setShadowLayer(0, 0, 0, 0);
        }
    }

    /**
     * Reset players and ball position for a new round.
     */
    private void setupNewRound() {
        mBall.cx = mCanvasWidth / 2;
        mBall.cy = mCanvasHeight / 2;
        mBall.dx = -PHYS_BALL_SPEED;
        mBall.dy = 0;

        movePlayer(mHumanPlayer,
                2,
                (mCanvasHeight - mHumanPlayer.paddleHeight) / 2);

        movePlayer(mComputerPlayer,
                mCanvasWidth - mComputerPlayer.paddleWidth - 2,
                (mCanvasHeight - mComputerPlayer.paddleHeight) / 2);
    }

    private void setStatusText(String text) {
        Message msg = mStatusHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("text", text);
        b.putInt("vis", View.VISIBLE);
        msg.setData(b);
        mStatusHandler.sendMessage(msg);
    }

    private void hideStatusText() {
        Message msg = mStatusHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt("vis", View.INVISIBLE);
        msg.setData(b);
        mStatusHandler.sendMessage(msg);
    }

    private void setScoreText(String text) {
        Message msg = mScoreHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("text", text);
        msg.setData(b);
        mScoreHandler.sendMessage(msg);
    }

    private void movePlayer(Player player, float left, float top) {
        if (left < 2) {
            left = 2;
        } else if (left + player.paddleWidth >= mCanvasWidth - 2) {
            left = mCanvasWidth - player.paddleWidth - 2;
        }
        if (top < 0) {
            top = 0;
        } else if (top + player.paddleHeight >= mCanvasHeight) {
            top = mCanvasHeight - player.paddleHeight - 1;
        }
        player.bounds.offsetTo(left, top);
    }

    private boolean collision(Player player, Ball ball) {
        return player.bounds.intersects(
                ball.cx - mBall.radius,
                ball.cy - mBall.radius,
                ball.cx + mBall.radius,
                ball.cy + mBall.radius);
    }

    /**
     * Compute ball direction after collision with player paddle.
     */
    private void handleCollision(Player player, Ball ball) {
        float relativeIntersectY = player.bounds.top + player.paddleHeight / 2 - ball.cy;
        float normalizedRelativeIntersectY = relativeIntersectY / (player.paddleHeight / 2);
        double bounceAngle = normalizedRelativeIntersectY * PHYS_MAX_BOUNCE_ANGLE;

        ball.dx = (float) (-Math.signum(ball.dx) * PHYS_BALL_SPEED * Math.cos(bounceAngle));
        ball.dy = (float) (PHYS_BALL_SPEED * -Math.sin(bounceAngle));

        if (player == mHumanPlayer) {
            mBall.cx = mHumanPlayer.bounds.right + mBall.radius;
        } else {
            mBall.cx = mComputerPlayer.bounds.left - mBall.radius;
        }
    }

}
