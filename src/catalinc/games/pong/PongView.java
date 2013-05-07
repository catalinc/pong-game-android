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
     * The game thread that actually draws the animation and handles user input.
     */
    private PongThread mGameThread;

    /**
     * Text view to display game status (Win, Lose, Paused etc.).
     */
    private TextView mStatusView;

    /**
     * Text view to display game score.
     */
    private TextView mScoreView;

    public PongView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        mGameThread = new PongThread(holder, context,
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
                attributeSet
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
            mGameThread.pause();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mGameThread.setSurfaceSize(width, height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mGameThread.setRunning(true);
        mGameThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        mGameThread.setRunning(false);
        while (retry) {
            try {
                mGameThread.join();
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
                if (mGameThread.isBetweenRounds()) {
                    // resume game
                    mGameThread.setState(PongThread.STATE_RUNNING);
                } else {
                    if (mGameThread.isTouchOnHumanPaddle(event)) {
                        moving = true;
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (moving) {
                    mGameThread.handleMoveHumanPaddleEvent(event);
                }
                return true;
            case MotionEvent.ACTION_UP:
                moving = false;
            default:
                return true;
        }
    }

    public PongThread getGameThread() {
        return mGameThread;
    }

}
