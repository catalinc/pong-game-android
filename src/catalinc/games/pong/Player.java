package catalinc.games.pong;

import android.graphics.Paint;
import android.graphics.RectF;

class Player {

    int paddleWidth;
    int paddleHeight;
    Paint paint;
    int score;
    RectF bounds;
    int collision;

    Player(int paddleWidth, int paddleHeight, Paint paint) {
        this.paddleWidth = paddleWidth;
        this.paddleHeight = paddleHeight;
        this.paint = paint;
        this.score = 0;
        this.bounds = new RectF(0, 0, paddleWidth, paddleHeight);
        this.collision = 0;
    }

}
