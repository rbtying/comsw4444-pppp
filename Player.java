package pppp.g7;

import pppp.sim.Point;
import pppp.sim.Move;

import java.util.*;

public class Player implements pppp.sim.Player {

    // see details below
    private int id = -1;
    private int side = 0;
    private long tick;
    private Util util;

    private Point[][] prevPiperPos;
    private Move[][] piperVel;
    private PlayerState[] states;

    private interface PlayerState {
        PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos);

        Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos);

        boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos);
    }

    private abstract class StopState implements PlayerState {
        public int iterations;
        public boolean playing;

        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            iterations--;
            return new Move(0, 0, playing);
        }

        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            return iterations <= 0;
        }

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
        	return util.moveToLoc(piperPos[id][pidx], dest, playing);
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
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            // return new SweepState();
            return new RetrieveMostRatsState();
        }
    }

    private class RetrieveClosestRatState extends DoorState {

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            Point closest_rat = get_closest_rats(pidx, piperPos, piperVel, pipers_played, ratPos);
            return new GoToLocationState(closest_rat, false) {
                @Override
                public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    List<Integer> rats = util.getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10);
                    if (!rats.isEmpty() && ratPos.length < piperPos[pidx].length) {
                        // there's a rat nearby! force nextstate to be called so we go for that instead
                        return true;
                    } else {
                        return super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);
                    }
                }

                @Override
                public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    List<Integer> rats = util.getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10);

                    if (rats.isEmpty() && ratPos.length < piperPos[id].length) {
                        return new RetrieveClosestRatState();
                    } else {
                        return new DepositState();
                    }
                }
            };
        }
    }

    private class RetrieveMostRatsState implements PlayerState {

        public static final double TOLERANCE = 0.05;

        private int max_rats = 10000000; // Force Update
        private Point target = new Point(0, 0);

        int counter = 0;

        @Override
        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            List<Integer> pos = util.getIndicesWithinDistance(target, ratPos, 10.0);
            if (pos.size() <= max_rats * .75) {
                update_most_rats(pidx, piperPos, piperVel, pipers_played, ratPos);
            }
            counter = (counter + 1); // % 10;
            return util.moveToLoc(piperPos[id][pidx], target, false);
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            List<Integer> pos = util.getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10.0);
            List<Integer> pos2 = util.getIndicesWithinDistance(target, ratPos, 10.0);
            return pos.size() >= pos2.size();// - 1;
        }

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            return new DepositState();
        }

        private void update_most_rats(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos)
        {
            max_rats = 1;
            target = ratPos[(int)(Math.random() * ratPos.length)];

            ArrayList<Point> realRatPos = new ArrayList<>();
            for (Point p : ratPos) {
                boolean ok = true;
                for (int i = 0; i < piperPos[id].length; i++) {
                    if (i != pidx && pipers_played[id][i]) {
                        if (piperPos[id][i].distance(p) <= 10) {
                            ok = false;
                        }
                    }
                }
                if (ok) {
                    realRatPos.add(p);
                }
            }

            for (int i = 0; i < realRatPos.size(); i++) {
                for (int j = i + 1; j < realRatPos.size(); j++) {
                    if (ratPos[i].distance(ratPos[j]) <= 10 * 2) {
                        Point p1 = getCenterByRadiusAndPoints(ratPos[i], ratPos[j], 10);
                        Point p2 = getCenterByRadiusAndPoints(ratPos[j], ratPos[i], 10);
                        int s1 = 0, s2 = 0;
                        for (Point k : realRatPos) {
                            if (k.distance(p1) <= 10) s1++;
                            if (k.distance(p2) <= 10) s2++;
                        }
                        if (s1 > max_rats) {
                            max_rats = s1; target = p1;
                        }
                        if (s2 > max_rats) {
                            max_rats = s2; target = p2;
                        }

                    }
                }
            }

        }

        private Point getCenterByRadiusAndPoints(Point a, Point b, double radius) {

            Point mid = new Point((a.x + b.x) / 2, (a.y + b.y) / 2);
            double angleMidToCenter = Math.atan2(b.y - a.y, b.x - a.x) + Math.PI / 2;

            double distAToB = a.distance(b);
            double distMidToCenter = Math.sqrt(radius * radius - distAToB * distAToB);

            return new Point(mid.x + distMidToCenter * Math.cos(angleMidToCenter),
                    mid.y + distMidToCenter * Math.sin(angleMidToCenter));

        }

    }

    private class SweepState extends GoToLocationState {

        public SweepState() {
            super(new Point(0, 0), false);
        }

        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            int max_pidx = piperPos[id].length;

            this.dest = new Point(side * ((pidx + 1) * 1.0 / (max_pidx + 1)) - side / 2, 0);
            return super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);
        }

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            return new PlayerState() {
                @Override
                public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    return new DepositState() {
                        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                            return new RetrieveClosestRatState();
                        }
                    };
                }

                @Override
                public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    return new Move(0, 0.1, true);
                }

                @Override
                public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    return side / 2 - piperPos[id][pidx].y < 10;
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
        private static final double MAX_WAIT_TICKS = 2 * 10 / 0.01;

        private long startTick;
        private int numRatsAtLastCheck;
        private boolean inApproach;

        public DepositState() {
            super(new Point(0, side / 2), true);
            inApproach = true;
            this.startTick = -1;
            this.numRatsAtLastCheck = 0;
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            boolean atLoc = super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);

            List<Integer> rats = util.getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10);
            this.numRatsAtLastCheck = rats.size();
            if (rats.isEmpty()) {
                return true;
            }

            if (!atLoc) {
                return false;
            }

            if (inApproach) {
                // finish approach
                inApproach = false;
                this.dest = new Point(0, side / 2 + 2.1);
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

            return false;

        }

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
            return new DoorState();
        }

        @Override
        public String toString() {
            return this.getClass().getCanonicalName() + " num rats: " + numRatsAtLastCheck + " time remaining: " + (MAX_WAIT_TICKS - (tick - startTick));
        }
    }



    // specify location that the player will alternate between
    public void init(int id, int side, long turns,
                     Point[][] pipers, Point[] rats) {
        this.tick = 0;
        this.id = id;
        this.side = side;
        util = new Util(id == 3 || id == 2, id == 2 || id == 1, id == 1 || id == 3);

        this.prevPiperPos = new Point[pipers.length][pipers[0].length];
        this.piperVel = new Move[pipers.length][pipers[0].length];

        
        for (int pid = 0; pid < pipers.length; ++pid) {
            for (int p = 0; p < pipers[pid].length; ++p) {
                this.prevPiperPos[pid][p] = util.transformPoint(pipers[pid][p]);
                this.piperVel[pid][p] = new Move(0, 0, false);
            }
        }

        this.states = new PlayerState[pipers.length];
        for (int i = 0; i < pipers.length; ++i) {
            this.states[i] = new DoorState() {
                @Override
                public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
                    return new SweepState();
                }
            };
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
                transformedPiperPos[pid][p] = util.transformPoint(pipers[pid][p]);
                double dx = transformedPiperPos[pid][p].x - prevPiperPos[pid][p].x;
                double dy = transformedPiperPos[pid][p].y - prevPiperPos[pid][p].y;
                this.piperVel[pid][p] = new Move(dx, dy, pipers_played[pid][p]);
                prevPiperPos[pid][p] = transformedPiperPos[pid][p];
            }
        }

        for (int r = 0; r < rats.length; ++r) {
            transformedRatPos[r] = util.transformPoint(rats[r]);
        }

        // perform computation
        Move m[] = play_transformed(id, side, transformedPiperPos, piperVel, pipers_played, transformedRatPos);

        // untransform coordinates
        for (int i = 0; i < m.length; ++i) {
            moves[i] = util.transformMove(m[i]);
        }
    }

    private Point get_closest_rats(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos)
    {
    	ArrayList<Double> distances = new ArrayList<Double>();
    	HashMap<Double, Point> rat_distances = new HashMap<Double, Point>();
        for (Point rat_position : ratPos) {
            double distance = piperPos[id][pidx].distance(rat_position);
            rat_distances.put(distance, rat_position);
            distances.add(distance);
        }
        Collections.sort(distances);

        // more pipers left than rats, double up on random rat
        if(ratPos.length < piperPos.length && pidx >= ratPos.length)
        {
        	pidx = (int)(Math.random()*ratPos.length);
        }
        return rat_distances.get(distances.get(pidx));
    }


    private Move[] play_transformed(int id, int side, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos) {
        // THE ENEMIES GATE IS DOWN!!!
        Move m[] = new Move[piperPos[id].length];

        // state machine
        for (int p = 0; p < piperPos[id].length; ++p) {
            if (states[p].stateComplete(p, piperPos, piperVel, pipers_played, ratPos)) {
                System.out.print("transitioning piper " + p + " out of state " + states[p]);
                states[p] = states[p].nextState(p, piperPos, piperVel, pipers_played, ratPos);
                System.out.println(" and into " +  states[p]);
            }
            m[p] = states[p].computeMove(p, piperPos, piperVel, pipers_played, ratPos);
        }
        return m;
    }
}
