package pppp.g7;

import pppp.sim.Move;
import pppp.sim.Point;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by rbtying on 9/16/15.
 */
public class Util {
    public boolean neg_x, neg_y, swap_xy;

    public Util(boolean neg_x, boolean neg_y, boolean swap_xy) {
        this.neg_x = neg_x;
        this.neg_y = neg_y;
        this.swap_xy = swap_xy;
    }

    /**
     * Finds indices of points within a given distance
     * @param pos the position to compute from
     * @param otherPos the array of other positions
     * @param distance the maximum distance to compare
     * @return list of indices of points within distance.
     */
    public List<Integer> getIndicesWithinDistance(Point pos, Point[] otherPos, double distance) {
        List<Integer> indices = new LinkedList<Integer>();
        for (int i = 0; i < otherPos.length; ++i) {
            if (otherPos[i] != null && pos.distance(otherPos[i]) < distance) {
                indices.add(i);
            }
        }
        return indices;
    }


    /**
     * Note: transformPoint(transformPoint(p)) == p
     *
     * @param p point to transform
     * @return point transformed into unified coordinate system, or back
     */
    public Point transformPoint(Point p) {
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
    public Move transformMove(Move m) {
        double dx = m.dx;
        double dy = m.dy;

        if (neg_y) dy = -dy;
        if (neg_x) dx = -dx;
        return swap_xy ? new Move(-dy, -dx, m.play) : new Move(dx, dy, m.play);
    }

    // create move towards specified destination
    public static Move moveToLoc(Point src, Point dst, boolean play) {
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
}
