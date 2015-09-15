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
    private long tick;

    private Random gen = new Random();

    private Point[][] prevPiperPos;
    private Move[][] piperVel;
    private Point closest_rat;
    private PlayerState[] states;

    private interface PlayerState {
        public abstract PlayerState nextState(int pidx);

        public abstract Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos);

        public abstract boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos);
    }

    private abstract class GoToLocationState implements PlayerState {
        public static final double TOLERANCE = 0.05;
        public Point dest;
        public boolean playing;

        public GoToLocationState(Point dest, boolean playing) {
            this.dest = dest;
            this.playing = playing;
        }

        @Override
        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
        	return move(piperPos[id][pidx], dest, playing);
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            return piperPos[id][pidx].distance(dest) < TOLERANCE;
        }

        @Override
        public String toString() {
            return getClass().getCanonicalName() + " dest: " + dest.x + ", " + dest.y;
        }
        
    }

    private class DoorState extends GoToLocationState {
        public DoorState() {
            super(new Point(0, side / 2), false);
        }

        @Override
        public PlayerState nextState(int pidx) {
            return new SweepState(closest_rat);
        }
    }

    private class SweepState extends GoToLocationState {

        public SweepState(Point destination) {
            super(destination, false);
        }

        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
        	boolean atLoc = super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);
            return atLoc;
        }

        @Override
        public PlayerState nextState(int pidx) {
            return new PlayerState() {
                @Override
                public PlayerState nextState(int pidx) {
                    return new DepositState();
                }

                @Override
                public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    return new Move(0- piperPos[id][pidx].x, side/2 - piperPos[id][pidx].y , true);
                }

                @Override
                public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    Point piper_position = piperPos[id][pidx];
                    Point target_position = new Point(0, side/2);
                	return piper_position.distance(target_position) < TOLERANCE;
                }

                @Override
                public String toString() {
                    return "Moving up state";
                }
            };
        }
    }

    private class DepositState extends GoToLocationState {
        // max rat distance divided by max rat speed
        private static final double MAX_WAIT_TICKS = 10 / 0.01;

        private long startTick;
        private int numRatsAtLastCheck;

        public DepositState() {
            super(new Point(0, side / 2 + 2.1), true);
            this.startTick = -1;
            this.numRatsAtLastCheck = 0;
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            boolean atLoc =  super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);

            if (!atLoc) {
                return false;
            }

            if (tick - startTick > MAX_WAIT_TICKS) {
                // bail out if it takes too long
                return true;
            }
            
            // we're at location now
            if (startTick == -1) {
                startTick = tick;
            }

            List<Integer> rats = getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10);

            this.numRatsAtLastCheck = rats.size();

            return rats.isEmpty();
        }

        @Override
        public PlayerState nextState(int pidx) {
            return new DoorState();
        }

        @Override
        public String toString() {
            return this.getClass().getCanonicalName() + " num rats: " + numRatsAtLastCheck + " time remaining: " + (MAX_WAIT_TICKS - (tick - startTick));
        }
    }

    /**
     * Finds indices of points within a given distance
     * @param pos the position to compute from
     * @param otherPos the array of other positions
     * @param distance the maximum distance to compare
     * @return list of indices of points within distance.
     */
    private List<Integer> getIndicesWithinDistance(Point pos, Point[] otherPos, double distance) {
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

    /**
     * Constrains point to within boundaries of walls
     *
     * @param p point to constrain
     * @return constrained point (potentially the same point)
     */
    private Point constrainPoint(Point p) {
        return p;
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
        this.tick = 0;
        this.id = id;
        this.side = side;
        this.neg_y = id == 2 || id == 1;
        this.neg_x = id == 3 || id == 2;
        this.swap_xy = id == 1 || id == 3;

        this.prevPiperPos = new Point[pipers.length][pipers[0].length];
        this.piperVel = new Move[pipers.length][pipers[0].length];
        this.closest_rat = new Point(0,0);

        
        for (int pid = 0; pid < pipers.length; ++pid) {
            for (int p = 0; p < pipers[pid].length; ++p) {
                this.prevPiperPos[pid][p] = this.transformPoint(pipers[pid][p]);
                this.piperVel[pid][p] = new Move(0, 0, false);
            }
        }

        this.states = new PlayerState[pipers.length];
        for (int i = 0; i < pipers.length; ++i) {
            this.states[i] = new DoorState();
        }
    }

    // return next locations on last argument
    public void play(Point[][] pipers, boolean[][] pipers_played,
                     Point[] rats, Move[] moves) {
        // increment tick
        ++tick;

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

        for (int r = 0; r < rats.length; ++r) {
            transformedRatPos[r] = transformPoint(rats[r]);
        }

        // perform computation
        Move m[] = play_transformed(id, side, transformedPiperPos, piperVel, pipers_played, transformedRatPos);

        // untransform coordinates
        for (int i = 0; i < m.length; ++i) {
            moves[i] = transformMove(m[i]);
        }
    }
    private Point get_closest_rats(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos)
    {
    	ArrayList<Double> distances = new ArrayList<Double>();
    	HashMap<Double, Point> rat_distances = new HashMap<Double, Point>();
        for(int r = 0; r < ratPos.length; r++)
        {
        	Point rat_position = ratPos[r];
        	double distance = piperPos[id][pidx].distance(rat_position);
        	rat_distances.put(distance, rat_position);
        	distances.add(distance);
        }
        Collections.sort(distances);
        //more pipers left than rats, double up on random rat
        if(ratPos.length < piperPos.length && pidx >= ratPos.length)
        {
        	pidx = (int)(Math.random()*ratPos.length);
        }
        closest_rat = rat_distances.get(distances.get(pidx));
        return new Point(closest_rat.x, closest_rat.y);
    }
    
    private Move[] play_transformed(int id, int side, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
        // THE ENEMIES GATE IS DOWN!!!
        Move m[] = new Move[piperPos[id].length];

        // state machine
        
        for (int p = 0; p < piperPos[id].length; ++p) {
        	get_closest_rats(p, piperPos, piperVel, pipers_played, ratPos);
            if (states[p].stateComplete(p, piperPos, piperVel, pipers_played, ratPos)) {
                System.out.print("transitioning piper " + p + " out of state " + states[p]);
                states[p] = states[p].nextState(p);
                System.out.println(" and into " +  states[p]);
            }
            m[p] = states[p].computeMove(p, piperPos, piperVel, pipers_played, ratPos);
        }
        return m;
    }
}
