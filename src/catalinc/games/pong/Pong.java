package catalinc.games.pong;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import static catalinc.games.pong.PongView.PongThread;

/**
 * Pong game.
 */
public class Pong extends Activity {

    private static final int MENU_PAUSE = 1;
    private static final int MENU_RESUME = 2;
    private static final int MENU_START = 3;
    private static final int MENU_STOP = 4;

    private PongThread mPongThread;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pong_layout);

        final PongView mPongView = (PongView) findViewById(R.id.pong);
        mPongView.setTextView((TextView) findViewById(R.id.text));
        mPongThread = mPongView.getThread();

        if (savedInstanceState == null) {
            mPongThread.setState(PongThread.STATE_READY);
        } else {
            mPongThread.restoreState(savedInstanceState);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPongThread.pause();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mPongThread.saveState(outState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_START, 0, R.string.menu_start);
        menu.add(0, MENU_STOP, 0, R.string.menu_stop);
        menu.add(0, MENU_PAUSE, 0, R.string.menu_pause);
        menu.add(0, MENU_RESUME, 0, R.string.menu_resume);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_START:
                mPongThread.doStart();
                return true;
            case MENU_STOP:
                mPongThread.setState(PongThread.STATE_LOSE);
                return true;
            case MENU_PAUSE:
                mPongThread.pause();
                return true;
            case MENU_RESUME:
                mPongThread.unPause();
                return true;
        }
        return false;
    }

}
