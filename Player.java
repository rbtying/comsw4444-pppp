package pppp.g7;

import pppp.sim.Move;
import pppp.sim.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Player implements pppp.sim.Player {

    // see details below
    private int id = -1;
    private int side = 0;
    private long tick;
    private Util util;

    private Point[][] prevPiperPos;
    private Move[][] piperVel;
    private PlayerState[] states;
    private HashMap<Integer, Integer> groupLookup;
    private HashMap<Integer, List<Integer>> groupReverseLookup;

    // specify location that the player will alternate between
    public void init(int id, int side, long turns,
                     Point[][] pipers, Point[] rats) {
        this.tick = 0;
        this.id = id;
        this.side = side;
        groupLookup = new HashMap<>(pipers[id].length);
        groupReverseLookup = new HashMap<>(pipers[id].length);
        int GROUPSIZE = 2;

        util = new Util(id == 3 || id == 2, id == 2 || id == 1, id == 1 || id == 3);

        this.prevPiperPos = new Point[pipers.length][pipers[0].length];
        this.piperVel = new Move[pipers.length][pipers[0].length];

        for (int pid = 0; pid < pipers.length; ++pid) {
            for (int p = 0; p < pipers[pid].length; ++p) {
                if (pid == id) {
                    // assume that points are in order
                    int group = p / GROUPSIZE;
                    groupLookup.put(p, group);
                    if (p % GROUPSIZE == 0) {
                        groupReverseLookup.put(group, new ArrayList<>(GROUPSIZE));
                    }
                    groupReverseLookup.get(group).add(p);
                }
                this.prevPiperPos[pid][p] = util.transformPoint(pipers[pid][p]);
                this.piperVel[pid][p] = new Move(0, 0, false);
            }
        }

        this.states = new PlayerState[pipers[id].length];
        for (int i = 0; i < pipers[id].length; ++i) {
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

    private Move[] play_transformed(int id, int side, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                    Point[] ratPos) {
        // THE ENEMIES GATE IS DOWN!!!
        Move m[] = new Move[piperPos[id].length];

        // state machine
        for (int p = 0; p < piperPos[id].length; ++p) {
            if (states[p].stateComplete(p, piperPos, piperVel, pipers_played, ratPos)) {
                System.out.print("piper: " + p + " | " + states[p]);
                states[p] = states[p].nextState(p, piperPos, piperVel, pipers_played, ratPos);
                System.out.println(" -> " + states[p]);
            }
            m[p] = states[p].computeMove(p, piperPos, piperVel, pipers_played, ratPos);
        }
        return m;
    }

    public interface PlayerState {
        PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                              Point[] ratPos);

        Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played, Point[] ratPos);

        boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                              Point[] ratPos);

        boolean sameStateAs(PlayerState other);
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
        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                Point[] ratPos) {
            return Util.moveToLoc(piperPos[id][pidx], dest, playing);
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            return piperPos[id][pidx].distance(dest) < TOLERANCE;
        }

        @Override
        public String toString() {
            return util.stateName(this) + String.format(" [dest: (%.02f, %.02f)]", dest.x, dest.y);
        }

        @Override
        public boolean sameStateAs(PlayerState other) {
            Point otherDest = ((GoToLocationState) other).dest;
            return otherDest.x == dest.x && otherDest.y == dest.y;
        }
    }

    private class DoorState extends GoToLocationState {
        public DoorState() {
            super(new Point(0, side / 2), false);
        }

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            double rat_density = ratPos.length * 1.0 / (side * side);
            double player_density = piperPos[id].length * 1.0 / (side * side);

            if (rat_density > 0.005) {
                return new SweepState();
            } else if ((rat_density / player_density) >= 3) {
                if (pidx < 2) {
                    return new RetrieveMostRatsState();
                } else {
                    return new RetrieveClosestRatState();
                }
            } else {
                return new RetrieveClosestRatState();
            }
        }

        @Override
        public String toString() {
            return util.stateName(this);
        }
    }

    public class RetrieveClosestRatState extends GoToLocationState {
        private static final int REACQUIRE_TICKS = 1;
        private static final int DEPTH = 1;
        public int targetRat;
        private long startTick;

        public RetrieveClosestRatState() {
            super(new Point(0, 0), false);
            startTick = tick;
            targetRat = -1;
        }

        @Override
        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                Point[] ratPos) {
            if ((tick - startTick) % REACQUIRE_TICKS == 0 || dest.distance(piperPos[id][pidx]) < 1) {
                targetRat = util.getClosestRat(id, states, pidx, piperPos, piperVel, pipers_played, ratPos, DEPTH);
                dest = ratPos[targetRat];
            }
            return super.computeMove(pidx, piperPos, piperVel, pipers_played, ratPos);
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            List<Integer> rats = util.getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10);

            if (ratPos.length <= piperPos[id].length) {
                return super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);
            }

            int numRats = rats.size();

            for (Integer r : rats) {
                int localdepth = 0;
                for (Integer p : util.getIndicesWithinDistance(ratPos[r], piperPos[id], 10)) {
                    if (p == pidx) {
                        continue;
                    }

                    if (states[p] instanceof DepositState) {
                        ++localdepth;
                    }
                }
                if (localdepth >= DEPTH) {
                    --numRats;
                }
            }

            return numRats > 0;// && super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);
        }

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            return new DepositState();
        }

        @Override
        public String toString() {
            return util.stateName(this);
        }
    }

    private class RetrieveMostRatsState implements PlayerState {

        public static final double TOLERANCE = 0.05;
        int counter = 0;
        private int max_rats = 10000000; // Force Update
        private Point target = new Point(0, 0);

        @Override
        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                Point[] ratPos) {
            List<Integer> pos = util.getIndicesWithinDistance(target, ratPos, 10.0);
            if (pos.size() <= max_rats * .75) {
                update_most_rats(pidx, piperPos, piperVel, pipers_played, ratPos);
            }
            counter = (counter + 1); // % 10;
            return Util.moveToLoc(piperPos[id][pidx], target, false);
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            List<Integer> pos = util.getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10.0);
            List<Integer> pos2 = util.getIndicesWithinDistance(target, ratPos, 10.0);
            return max_rats <= 1 || pos.size() >= pos2.size();// - 1;
        }

        @Override
        public boolean sameStateAs(PlayerState other) {
            return true;
        }

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            List<Integer> rats = util.getIndicesWithinDistance(piperPos[id][pidx], ratPos, 10);
            if (rats.isEmpty()) {
                if (max_rats <= 1) {
                    return new RetrieveClosestRatState();
                } else {
                    return new RetrieveMostRatsState();
                }
            } else {
                return new DepositState();
            }
        }

        private void update_most_rats(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                      Point[] ratPos) {
            max_rats = 1;
            target = ratPos[(int) (Math.random() * ratPos.length)];

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
                            if (k.distance(p1) <= 10) {
                                s1++;
                            }
                            if (k.distance(p2) <= 10) {
                                s2++;
                            }
                        }
                        if (s1 > max_rats) {
                            max_rats = s1;
                            target = p1;
                        }
                        if (s2 > max_rats) {
                            max_rats = s2;
                            target = p2;
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


        @Override
        public String toString() {
            return util.stateName(this);
        }
    }

    private class SweepState extends GoToLocationState {

        public SweepState() {
            super(new Point(0, 0), false);
        }

        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            int max_pidx = piperPos[id].length;

            // group into sets of 2
            this.dest = new Point(side * ((pidx + 1) * 1.0 / (max_pidx + 1)) - side / 2, -side / 4);
            return super.stateComplete(pidx, piperPos, piperVel, pipers_played, ratPos);
        }

        @Override
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            return new GroupWaitState() {
                @Override
                public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                             Point[] ratPos) {
                    return new PlayerState() {
                        @Override
                        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel,
                                                     boolean[][] pipers_played, Point[] ratPos) {
                            return new DepositState() {
                                public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel,
                                                             boolean[][] pipers_played, Point[] ratPos) {
                                    return new RetrieveClosestRatState();
                                }
                            };
                        }

                        @Override
                        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel,
                                                boolean[][] pipers_played, Point[] ratPos) {
                            return new Move(0, 0.1, true);
                        }

                        @Override
                        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel,
                                                     boolean[][] pipers_played, Point[] ratPos) {
                            return side / 2 - piperPos[id][pidx].y < 10;
                        }

                        @Override
                        public boolean sameStateAs(PlayerState other) {
                            return true;
                        }

                        @Override
                        public String toString() {
                            return "MovingUpState";
                        }
                    };
                }
            };
        }

        @Override
        public String toString() {
            return util.stateName(this);
        }
    }

    private abstract class GroupWaitState implements PlayerState {
        public boolean letsGo;
        protected int group;

        public GroupWaitState() {
            letsGo = false;
            group = -1;
        }

        @Override
        public Move computeMove(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                Point[] ratPos) {
            group = groupLookup.get(pidx);
            return new Move(0, 0, true);
        }

        @Override
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            if (letsGo) {
                return true;
            }
            group = groupLookup.get(pidx);
            List<Integer> l = groupReverseLookup.get(group);
            boolean shouldGo = util.groupIsInState(l, states, this);
            if (shouldGo) {
                for (Integer i : l) {
                    ((GroupWaitState) states[i]).letsGo = true;
                }
            }
            return false;
        }

        @Override
        public boolean sameStateAs(PlayerState other) {
            return true;
        }

        @Override
        public String toString() {
            return util.stateName(this) + " [group: " + group + "]";
        }
    }

    public class DepositState extends GoToLocationState {
        // max rat distance divided by max rat speed
        private static final double MAX_WAIT_TICKS = 2 * 10 / 0.01 + 10e9;

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
        public boolean stateComplete(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
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
        public PlayerState nextState(int pidx, Point[][] piperPos, Move[][] piperVel, boolean[][] pipers_played,
                                     Point[] ratPos) {
            return new DoorState();
        }

        @Override
        public String toString() {
            return util.stateName(this) + " [rats: " + numRatsAtLastCheck + "]";
        }
    }
}
