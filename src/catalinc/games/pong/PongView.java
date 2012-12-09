package catalinc.games.pong;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

/**
 * A simple Pong game.
 */
public class PongView extends SurfaceView implements SurfaceHolder.Callback {

    /**
     * The animationThread that actually draws the animation and handles user input.
     */
    private PongThread animationThread;

    /**
     * Text view to display game status (Win, Lose, Paused etc.).
     */
    private TextView mStatusView;

    /**
     * Text view to display game score.
     */
    private TextView mScoreView;

    public PongView(Context context, AttributeSet attrs) {
        super(context, attrs);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        animationThread = new PongThread(holder, context,
                new Handler() {
                    @Override
                    public void handleMessage(Message m) {
                        mStatusView.setVisibility(m.getData().getInt("vis"));
                        mStatusView.setText(m.getData().getString("text"));
                    }
                },
                new Handler() {
                    @Override
                    public void handleMessage(Message m) {
                        mScoreView.setText(m.getData().getString("text"));
                    }
                },
                attrs
        );

        setFocusable(true);
    }

    /**
     * @param textView to be used for status messages
     */
    public void setStatusView(TextView textView) {
        mStatusView = textView;
    }

    /**
     * @param textView to be used to display score
     */
    public void setScoreView(TextView textView) {
        mScoreView = textView;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) {
            animationThread.pause();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        animationThread.setSurfaceSize(width, height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        animationThread.setRunning(true);
        animationThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        animationThread.setRunning(false);
        while (retry) {
            try {
                animationThread.join();
                retry = false;
            } catch (InterruptedException e) {
                // don't care
            }
        }
    }

    private boolean moving;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (animationThread.isBetweenRounds()) {
                    // resume game
                    animationThread.setState(PongThread.STATE_RUNNING);
                } else {
                    if (animationThread.isTouchOnHumanPaddle(event)) {
                        moving = true;
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (moving) {
                    animationThread.handleMoveHumanPaddleEvent(event);
                }
                return true;
            case MotionEvent.ACTION_UP:
                moving = false;
            default:
                return true;
        }
    }

    /**
     * @return animation animationThread
     */
    public PongThread getAnimationThread() {
        return animationThread;
    }

}
