package pppp.g7;

import pppp.sim.Point;
import pppp.sim.Move;

import java.util.*;

public class Player implements pppp.sim.Player {

    // see details below
    private int id = -1;
    private int side = 0;
    private boolean neg_x;
    private boolean neg_y;
    private boolean swap_xy;

    private Random gen = new Random();

    private Point[][] prevPiperPos;
    private Move[][] piperVel;


    /**
     * Note: transformPoint(transformPoint(p)) == p
     *
     * @param p point to transform
     * @return point transformed into unified coordinate system, or back
     */
    private Point transformPoint(Point p) {
        double x = p.x;
        double y = p.y;
        if (neg_y) y = -y;
        if (neg_x) x = -x;
        return swap_xy ? new Point(y, x) : new Point(x, y);
    }

    /**
     * Note: transformMove(transformMove(m)) == m
     *
     * @param m move to transform
     * @return move transformed into unified coordinate system, or back
     */
    private Move transformMove(Move m) {
        double dx = m.dx;
        double dy = m.dy;

        if (neg_y) dy = -dy;
        if (neg_x) dx = -dx;
        return swap_xy ? new Move(-dy, -dx, m.play) : new Move(dx, dy, m.play);
    }

    // create move towards specified destination
    private static Move move(Point src, Point dst, boolean play) {
        double dx = dst.x - src.x;
        double dy = dst.y - src.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        double limit = play ? 0.1 : 0.5;
        if (length > limit) {
            dx = (dx * limit) / length;
            dy = (dy * limit) / length;
        }
        return new Move(dx, dy, play);
    }

    // specify location that the player will alternate between
    public void init(int id, int side, long turns,
                     Point[][] pipers, Point[] rats) {
        this.id = id;
        this.side = side;
        this.neg_y = id == 2 || id == 1;
        this.neg_x = id == 3 || id == 2;
        this.swap_xy = id == 1 || id == 3;

        this.prevPiperPos = new Point[pipers.length][pipers[0].length];
        this.piperVel = new Move[pipers.length][pipers[0].length];

        for (int pid = 0; pid < pipers.length; ++pid) {
            for (int p = 0; p < pipers[pid].length; ++p) {
                this.prevPiperPos[pid][p] = this.transformPoint(pipers[pid][p]);
                this.piperVel[pid][p] = new Move(0, 0, false);
            }
        }
    }

    // return next locations on last argument
    public void play(Point[][] pipers, boolean[][] pipers_played,
                     Point[] rats, Move[] moves) {

        // transform coordinates
        Point transformedPiperPos[][] = new Point[pipers.length][pipers[0].length];
        Point transformedRatPos[] = new Point[rats.length];

        for (int pid = 0; pid < pipers.length; ++pid) {
            for (int p = 0; p < pipers[pid].length; ++p) {
                transformedPiperPos[pid][p] = this.transformPoint(pipers[pid][p]);
                double dx = transformedPiperPos[pid][p].x - prevPiperPos[pid][p].x;
                double dy = transformedPiperPos[pid][p].y - prevPiperPos[pid][p].y;
                this.piperVel[pid][p] = new Move(dx, dy, pipers_played[pid][p]);
                prevPiperPos[pid][p] = transformedPiperPos[pid][p];
            }
        }

        // perform computation
        Move m[] = play_transformed(id, side, transformedPiperPos, piperVel, pipers_played, transformedRatPos);

        // untransform coordinates
        for (int i = 0; i < m.length; ++i) {
            moves[i] = transformMove(m[i]);
        }
    }

    private static Move[] play_transformed(int id, int side, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
        // THE ENEMIES GATE IS DOWN!!!
        Point door = new Point(0, side / 2);

        Move m[] = new Move[piperPos[id].length];
        for (int p = 0; p < piperPos[id].length; ++p) {
            // m[p] = new Move(-1, 0, false);
            m[p] = move(piperPos[id][p], door, false);
        }
        return m;
    }
}
