package catalinc.games.pong;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

/**
 * A simple Pong game.
 */
public class PongView extends SurfaceView implements SurfaceHolder.Callback {

    /**
     * The thread that actually draws the animation and handles user input.
     */
    private PongThread thread;

    /**
     * Text view to display game status (Win, Lose, Paused etc.).
     */
    private TextView mStatusText;

    public PongView(Context context, AttributeSet attrs) {
        super(context, attrs);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        thread = new PongThread(holder, context, new Handler() {
            @Override
            public void handleMessage(Message m) {
                mStatusText.setVisibility(m.getData().getInt("vis"));
                mStatusText.setText(m.getData().getString("text"));
            }
        }, attrs);

        setFocusable(true);
    }

    /**
     * Set the text view used for messages.
     *
     * @param textView to be used for status messages
     */
    public void setTextView(TextView textView) {
        mStatusText = textView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) {
            thread.pause();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        thread.setSurfaceSize(width, height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        thread.setRunning(true);
        thread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                // don't care
            }
        }
    }

    private boolean moving;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (thread.touchOnPlayerAPaddle(event)) {
                    moving = true;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (moving) {
                    thread.movePlayerAPaddle(event);
                }
                return true;
            case MotionEvent.ACTION_UP:
                moving = false;
            default:
                return true;
        }
    }

    /**
     * @return animation thread
     */
    public PongThread getThread() {
        return thread;
    }

    /**
     * Handle animation, game logic and user input.
     */
    static class PongThread extends Thread {

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
        private static final int PHYS_PADDLE_SPEED = 5;
        private static final int PHYS_FPS_INIT = 40;
        private static final double PHYS_MAX_BOUNCE_ANGLE = 5 * Math.PI / 12; // radians, 75 degree

        /*
        * Constants used when game state is saved/restored
        */
        private static final String KEY_PLAYER_A_DATA = "playerA";
        private static final String KEY_PLAYER_B_DATA = "playerB";
        private static final String KEY_BALL_DATA = "ball";
        private static final String KEY_FPS = "fps";

        /**
         * Handle to the surface manager object we interact with
         */
        private final SurfaceHolder mSurfaceHolder;

        /**
         * Message handler used by thread to interact with TextView
         */
        private Handler mHandler;

        /**
         * Handle to the application context
         */
        private Context mContext;

        /**
         * Indicate whether the surface has been created & is ready to draw
         */
        private boolean mRun = false;

        /**
         * The state of the game.
         * One of state constants.
         */
        private int mMode;

        /**
         * Current height of the canvas.
         */
        private int mCanvasHeight = 1;

        /**
         * Current width of the canvas.
         */
        private int mCanvasWidth = 1;

        /**
         * Used to compute elapsed time between frames
         */
        private long mLastTime;

        /**
         * Number of frames per second.
         */
        private int mFramesPerSecond;

        /*
         * Dimensions of game objects
         */
        private int mPaddleHeight;
        private int mPaddleWidth;
        private int mBallRadius;
        private float mScoreTextSize;

        /*
        * Styles and colors used to draw game objects
        */
        private Paint mPlayerAPaint;
        private Paint mPlayerBPaint;
        private Paint mBallPaint;
        private Paint mMedianLinePaint;
        private Paint mScoreTextPaint;

        /*
         * Game objects
         */
        private Player mPlayerA;
        private Player mPlayerB;
        private Ball mBall;

        PongThread(final SurfaceHolder surfaceHolder,
                   final Context context,
                   final Handler handler,
                   final AttributeSet attrs) {
            mSurfaceHolder = surfaceHolder;
            mHandler = handler;
            mContext = context;

            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PongView);

            mPaddleHeight = a.getInt(R.styleable.PongView_paddleHeight, 85);
            mPaddleWidth = a.getInt(R.styleable.PongView_paddleWidth, 25);
            mBallRadius = a.getInt(R.styleable.PongView_ballRadius, 15);
            mScoreTextSize = a.getFloat(R.styleable.PongView_scoreTextSize, 12);

            a.recycle();

            mPlayerAPaint = new Paint();
            mPlayerAPaint.setAntiAlias(true);
            mPlayerAPaint.setColor(Color.BLUE);

            mPlayerBPaint = new Paint();
            mPlayerBPaint.setAntiAlias(true);
            mPlayerBPaint.setColor(Color.RED);

            mBallPaint = new Paint();
            mBallPaint.setAntiAlias(true);
            mBallPaint.setColor(Color.GREEN);

            mMedianLinePaint = new Paint();
            mMedianLinePaint.setAntiAlias(true);
            mMedianLinePaint.setColor(Color.WHITE);
            mMedianLinePaint.setAlpha(80);
            mMedianLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mMedianLinePaint.setStrokeWidth(3.0f);
            mMedianLinePaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));

            mScoreTextPaint = new Paint();
            mScoreTextPaint.setColor(Color.GRAY);
            mScoreTextPaint.setSubpixelText(true);
            mScoreTextPaint.setTextSize(mScoreTextSize);
        }

        /**
         * Save game state to the provided Bundle.
         *
         * @param map bundle to save game state
         */
        public void saveState(Bundle map) {
            synchronized (mSurfaceHolder) {
                map.putFloatArray(KEY_PLAYER_A_DATA,
                        new float[]{mPlayerA.left, mPlayerA.top, mPlayerA.score});
                map.putFloatArray(KEY_PLAYER_B_DATA,
                        new float[]{mPlayerB.left, mPlayerB.top, mPlayerB.score});
                map.putFloatArray(KEY_BALL_DATA,
                        new float[]{mBall.cx, mBall.cy, mBall.dx, mBall.dy});
                map.putInt(KEY_FPS, mFramesPerSecond);
            }
        }

        /**
         * Restores game state from specified bundle.
         *
         * @param map bundle containing the game state
         */
        public void restoreState(Bundle map) {
            synchronized (mSurfaceHolder) {
                float[] playerAData = map.getFloatArray(KEY_PLAYER_A_DATA);
                mPlayerA = new Player(playerAData[0], playerAData[1], (int) playerAData[2]);

                float[] playerBData = map.getFloatArray(KEY_PLAYER_B_DATA);
                mPlayerB = new Player(playerBData[0], playerBData[1], (int) playerBData[2]);

                float[] ballData = map.getFloatArray(KEY_BALL_DATA);
                mBall = new Ball(ballData[0], ballData[1], ballData[2], ballData[3]);

                mFramesPerSecond = map.getInt(KEY_FPS);
            }
        }

        /**
         * Game loop.
         */
        @Override
        public void run() {
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if (mMode == STATE_RUNNING) {
                            updatePhysics();
                        }
                        doDraw(c);
                    }
                } finally {
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }

        /**
         * Used to signal game thread whether it should be running or not.
         *
         * @param running true to run, false to shut down
         */
        public void setRunning(boolean running) {
            mRun = running;
        }

        /**
         * Sets the game mode.
         *
         * @param mode one of the state constants
         */
        public void setState(int mode) {
            synchronized (mSurfaceHolder) {
                mMode = mode;
                Resources res = mContext.getResources();
                switch (mMode) {
                    case STATE_READY:
                        mPlayerA = new Player(2, (mCanvasHeight - mPaddleHeight) / 2, 0);
                        mPlayerB = new Player(mCanvasWidth - mPaddleWidth - 2, (mCanvasHeight - mPaddleHeight) / 2, 0);
                        mBall = new Ball(mCanvasWidth / 2, mCanvasHeight / 2, -PHYS_BALL_SPEED, 0);
                        mFramesPerSecond = PHYS_FPS_INIT;
                        break;
                    case STATE_RUNNING:
                        hideStatusText();
                        break;
                    case STATE_WIN:
                        setStatusText(res.getString(R.string.mode_win));
                        mPlayerA.score++;
                        newRound();
                        break;
                    case STATE_LOSE:
                        setStatusText(res.getString(R.string.mode_lose));
                        mPlayerB.score++;
                        newRound();
                        break;
                    case STATE_PAUSE:
                        setStatusText(res.getString(R.string.mode_pause));
                        break;
                }
            }
        }

        /**
         * Callback invoked when the surface dimensions change.
         *
         * @param width  canvas width
         * @param height canvas height
         */
        public void setSurfaceSize(int width, int height) {
            synchronized (mSurfaceHolder) {
                mCanvasWidth = width;
                mCanvasHeight = height;

                newRound();
            }
        }

        /**
         * Start the game.
         */
        public void doStart() {
            synchronized (mSurfaceHolder) {
                mLastTime = System.currentTimeMillis();
                setState(STATE_RUNNING);
            }
        }

        /**
         * Pauses the animation.
         */
        public void pause() {
            synchronized (mSurfaceHolder) {
                if (mMode == STATE_RUNNING) {
                    setState(STATE_PAUSE);
                }
            }
        }

        /**
         * Resumes from a pause.
         */
        public void unPause() {
            synchronized (mSurfaceHolder) {
                mLastTime = System.currentTimeMillis();
                setState(STATE_RUNNING);
            }
        }

        /**
         * Returns true if the touch event is on Player A paddle bounds.
         *
         * @param event touch even
         * @return true if touch is on Player A paddle
         */
        private boolean touchOnPlayerAPaddle(MotionEvent event) {
            return event.getX() >= mPlayerA.left
                    && event.getX() <= mPlayerA.left + mPaddleWidth
                    && event.getY() >= mPlayerA.top
                    && event.getY() <= mPlayerA.top + mPaddleHeight;
        }

        /**
         * Move Player A paddle according to touch move event.
         *
         * @param event touch move event
         */
        private void movePlayerAPaddle(MotionEvent event) {
            synchronized (mSurfaceHolder) {
                mPlayerA.top = event.getY() - mPaddleHeight / 2;
                if (mPlayerA.top < 0) {
                    mPlayerA.top = 0;
                }
                if (mPlayerA.top + mPaddleHeight >= mCanvasHeight) {
                    mPlayerA.top = mCanvasHeight - mPaddleHeight - 1;
                }
            }
        }

        /**
         * Update paddle and player positions, check for collisions, win or lose.
         */
        private void updatePhysics() {
            long now = System.currentTimeMillis();

            if (now - mLastTime >= 1000 / mFramesPerSecond) {
                if (mBall.cy >= mPlayerA.top
                        && mBall.cy <= mPlayerA.top + mPaddleHeight
                        && mBall.cx - mBallRadius <= mPlayerA.left + mPaddleWidth) {
                    processCollision(mPlayerA, mBall);
                } else if (mBall.cy >= mPlayerB.top
                        && mBall.cy <= mPlayerB.top + mPaddleHeight
                        && mBall.cx + mBallRadius >= mPlayerB.left) {
                    processCollision(mPlayerB, mBall);
                } else if (mBall.cy - mBallRadius <= 0
                        || mBall.cy + mBallRadius >= mCanvasHeight) {
                    // collision with top or bottom walls
                    mBall.dy = -mBall.dy;
                } else if (mBall.cx + mBallRadius >= mCanvasWidth) {
                    // collision with right wall -> human win
                    setState(STATE_WIN);
                    return;
                } else if (mBall.cx <= mBallRadius) {
                    // collision with left wall -> computer win
                    setState(STATE_LOSE);
                    return;
                } else {
                    // do some AI
                    if (mPlayerB.top > mBall.cy + mBallRadius) {
                        // move up
                        movePaddle(mPlayerB, -PHYS_PADDLE_SPEED);
                    } else if (mPlayerB.top + mPaddleHeight < mBall.cy - mBallRadius) {
                        // move down
                        movePaddle(mPlayerB, PHYS_PADDLE_SPEED);
                    }
                }

                mBall.cx += mBall.dx;
                mBall.cy += mBall.dy;

                mLastTime = System.currentTimeMillis();
            }
        }

        /**
         * Draws the paddles and ball.
         *
         * @param canvas surface to draw on
         */
        private void doDraw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);

            final int middle = mCanvasWidth / 2;
            canvas.drawLine(middle, 10, middle, mCanvasHeight - 10, mMedianLinePaint);
            canvas.drawText(mPlayerA.score + "", middle - 20, 10, mScoreTextPaint);
            canvas.drawText(mPlayerB.score + "", middle + 10, 10, mScoreTextPaint);

            canvas.drawRoundRect(
                    new RectF(
                            mPlayerA.left,
                            mPlayerA.top,
                            mPlayerA.left + mPaddleWidth,
                            mPlayerA.top + mPaddleHeight),
                    5, 5,
                    mPlayerAPaint);
            canvas.drawRoundRect(
                    new RectF(
                            mPlayerB.left,
                            mPlayerB.top,
                            mPlayerB.left + mPaddleWidth,
                            mPlayerB.top + mPaddleHeight),
                    5, 5,
                    mPlayerBPaint);
            canvas.drawCircle(mBall.cx, mBall.cy, mBallRadius, mBallPaint);
        }

        private void newRound() {
            mBall.cx = mCanvasWidth / 2;
            mBall.cy = mCanvasHeight / 2;
            mBall.dx = -PHYS_BALL_SPEED;
            mBall.dy = 0;
            mPlayerA.left = 2;
            mPlayerA.top = (mCanvasHeight - mPaddleHeight) / 2;
            mPlayerB.left = mCanvasWidth - mPaddleWidth - 2;
            mPlayerB.top = (mCanvasHeight - mPaddleHeight) / 2;
            mFramesPerSecond = PHYS_FPS_INIT;
        }

        private void setStatusText(String text) {
            Message msg = mHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("text", text);
            b.putInt("vis", View.VISIBLE);
            msg.setData(b);
            mHandler.sendMessage(msg);
        }

        private void hideStatusText() {
            Message msg = mHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putInt("vis", View.INVISIBLE);
            msg.setData(b);
            mHandler.sendMessage(msg);
        }

        private void movePaddle(Player player, float speed) {
            player.top += speed;
            if (player.top < 0) {
                player.top = 0;
            }
            if (player.top + mPaddleHeight >= mCanvasHeight) {
                player.top = mCanvasHeight - mPaddleHeight - 1;
            }
        }

        private void processCollision(Player player, Ball ball) {
            float relativeIntersectY = player.top + mPaddleHeight / 2 - ball.cy;
            float normalizedRelativeIntersectY = relativeIntersectY / (mPaddleHeight / 2);
            double bounceAngle = normalizedRelativeIntersectY * PHYS_MAX_BOUNCE_ANGLE;

            ball.dx = (float) (-Math.signum(ball.dx) * PHYS_BALL_SPEED * Math.cos(bounceAngle));
            ball.dy = (float) (PHYS_BALL_SPEED * -Math.sin(bounceAngle));
        }

    }

    private static final class Player {

        float top;
        float left;
        int score;

        Player(float left, float top, int score) {
            this.left = left;
            this.top = top;
            this.score = score;
        }

    }

    private static final class Ball {

        float cx;
        float cy;
        float dx;
        float dy;

        Ball(float cx, float cy, float dx, float dy) {
            this.cx = cx;
            this.cy = cy;
            this.dx = dx;
            this.dy = dy;
        }

    }
}
