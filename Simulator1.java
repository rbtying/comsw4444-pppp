package pppp.sim;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.tools.*;

class Simulator1 {

	// random generator
	private Random gen = new Random();

	// root folder
	private final String root = "pppp";

	// default sizes
	private int inner_side = 100;
	private int outer_side = 10;
	private int door_length = 2;

	// speeds
	private double rat_speed = 0.1;
	private double piper_mute_speed = 0.5;
	private double piper_play_speed = 0.1;

	// music radius
	private int inner_radius =  2;
	private int outer_radius = 10;

	// turn limit (infinite if negative)
	private long turn_limit = -1;

	// time per webgui frame
	private int refresh = 20;

	// print messages to terminal
	private boolean verbose = false;

	// exit on player exception
	private boolean exit_on_exception = true;

	// group players
	private String[] groups = new String [4];
	private Class[] player_classes = new Class [4];
	private Player[] players = new Player [4];
	private int[] score = new int [4];
	private String[] direction = new String [4];

	// piper positions & moves
	private Point[][] pipers = null;
	private Move[][] moves = null;

	// copy arrays
	private Point[][] pipers_copy = null;
	private Point[][] pipers_copy_2D = null;
	private boolean[][] pipers_played_copy = null;
	private boolean[][] pipers_played_copy_2D = null;

	// rat positions & tunes
	private Point[] rats = null;
	private Point[] rats_copy = null;
	private int[] rat_tune = null;
	private double[] rat_angle = null;
	private boolean[] rat_random_angle = null;

	// copy data to avoid corruption by players
	private void copy()
	{
		if (pipers_copy == null) {
			pipers_copy = new Point [pipers.length][];
			pipers_copy_2D = new Point [pipers.length][];
			pipers_played_copy = new boolean [pipers.length][];
			pipers_played_copy_2D = new boolean [pipers.length][];
			for (int g = 0 ; g != pipers.length ; ++g) {
				pipers_copy_2D[g] = new Point [pipers[g].length];
				pipers_played_copy_2D[g] = new boolean [pipers[g].length];
			}
		}
		for (int g = 0 ; g != groups.length ; ++g) {
			pipers_copy[g] = pipers_copy_2D[g];
			pipers_played_copy[g] = pipers_played_copy_2D[g];
			for (int p = 0 ; p != pipers[g].length ; ++p) {
				pipers_copy[g][p] = pipers[g][p];
				pipers_played_copy[g][p] = moves[g][p] != null &&
				                           moves[g][p].play;
			}
		}
		if (rats_copy == null || rats_copy.length != rats.length)
			rats_copy = new Point [rats.length];
		for (int r = 0 ; r != rats.length ; ++r)
			rats_copy[r] = rats[r];
	}

	// initialize game
	private boolean init(int n_pipers, int n_rats)
	{
		pipers = new Point [4][n_pipers];
		moves = new Move [4][n_pipers];
		rats = new Point [n_rats];
		rat_tune = new int [n_rats];
		rat_angle = new double [n_rats];
		rat_random_angle = new boolean [n_rats];
		// initialize piper locations (specific)
		for (int p = 0 ; p != n_pipers ; ++p) {
			double d = (p + 1) * (inner_side / (double) (n_pipers + 1))
			                    - inner_side * 0.5;
			double s = inner_side * 0.5 + outer_side;
			pipers[0][p] = new Point(d, +s);  // north
			pipers[2][p] = new Point(d, -s);  // south
			pipers[1][p] = new Point(+s, d);  // east
			pipers[3][p] = new Point(-s, d);  // west
		}
		// initialize rat locations (random)
		for (int r = 0 ; r != n_rats ; ++r) {
			int xi = gen.nextInt(inner_side * 1000 - 2);
			int yi = gen.nextInt(inner_side * 1000 - 2);
			double x = xi * 0.001 - inner_side * 0.5;
			double y = yi * 0.001 - inner_side * 0.5;
			rats[r] = new Point(x, y);
			rat_tune[r] = -1;
			rat_random_angle[r] = false;
			rat_angle[r] = gen.nextDouble() * Math.PI * 2.0;
		}
		// initialize players
		for (int g = 0 ; g != 4 ; ++g) {
			players[g] = null;
			score[g] = 0;
			copy();
			try {
				players[g] = (Player) player_classes[g].newInstance();
				players[g].init(g, inner_side, turn_limit,
				                pipers_copy, rats_copy);
			} catch (Exception e) {
				if (players[g] == null)
					println("Exception by " + direction[g] +
					        " group " + groups[g] + " constructor");
				else
					println("Exception by " + direction[g] +
					        " group " + groups[g] + " init()");
				print(e);
				if (exit_on_exception) {
					if (players[g] == null)
						System.err.println("Group " + groups[g] + " threw"
						+ " exception on constructor: " + e.getMessage());
					else
						System.err.println("Group " + groups[g] + " threw"
						+ " exception on init(): " + e.getMessage());
					System.err.println("Exiting ...");
					System.exit(1);
				}
				players[g] = null;
			}
		}
		// check if there are any valid players
		for (int g = 0 ; g != 4 ; ++g)
			if (players[g] != null) return true;
		return false;
	}

	// next state of game
	private void next()
	{
		// get moves from players
		for (int g = 0 ; g != 4 ; ++g) {
			// clear array of moves
			for (int p = 0 ; p != pipers[g].length ; ++p)
				moves[g][p] = null;
			// ask player for next move
			if (players[g] != null) {
				copy();
				try {
					players[g].play(pipers_copy, pipers_played_copy,
					                rats_copy, moves[g]);
				} catch (RuntimeException e) {
					println("Exception by " + direction[g] +
					        " group " + groups[g] + " play()");
					print(e);
					if (exit_on_exception) {
						System.err.println("Group " + groups[g] + " threw"
						+ " exception on play(): " + e.getMessage());
						System.err.println("Exiting ...");
						System.exit(1);
					}
				}
			}
			for (int p = 0 ; p != pipers[g].length ; ++p)
				// if move is invalid immobilize piper
				if (moves[g][p] == null ||
				    Double.isNaN(moves[g][p].dx) || 
				    Double.isNaN(moves[g][p].dy) ||
				    Double.isInfinite(moves[g][p].dx) ||
				    Double.isInfinite(moves[g][p].dy)) {
					moves[g][p] = new Move(0.0, 0.0, false);
					println("Invalid move by group " + groups[g]);
				} else {
					// fix movement if speed exceeds limit
					double dx = moves[g][p].dx;
					double dy = moves[g][p].dy;
					boolean play = moves[g][p].play;
					double length = Math.hypot(dx, dy);
					double piper_speed = play ? piper_play_speed
					                          : piper_mute_speed;
					if (length > piper_speed) {
						dx = dx * piper_speed / length;
						dy = dy * piper_speed / length;
						moves[g][p] = new Move(dx, dy, play);
						println("Fixed move by group " + groups[g]);
					}
				}
		}
		// update rat positions
		for (int r = 0 ; r != rats.length ; ++r) {
			Point p1 = rats[r];
			// find dominant tune
			int cc = 0;
			int cg = -1;
			for (int g = 0 ; g != pipers.length ; ++g) {
				int c = 0;
				for (int p = 0 ; p != pipers[g].length ; ++p)
					if (moves[g][p].play &&
					    p1.distance(pipers[g][p]) <= 10.0) c++;
				if (c == cc) cg = -1;
				else if (c > cc) {
					cg = g;
					cc = c;
				}
			}
			rat_tune[r] = cg;
			// next location
			double dx = 0.0;
			double dy = 0.0;
			if (cg == -1) {
				// generate new random angle
				if (rat_random_angle[r]) {
					rat_random_angle[r] = false;
					rat_angle[r] = gen.nextDouble() * Math.PI * 2.0;
				}
				// use angle to get movements
				dx = rat_speed * Math.cos(rat_angle[r]);
				dy = rat_speed * Math.sin(rat_angle[r]);
			} else {
				// find closest piper of dominant group
				Point p2 = null;
				for (int p = 0 ; p != pipers[cg].length ; ++p) {
					Point p3 = pipers[cg][p];
					if (moves[cg][p].play && (p2 == null ||
						p1.distance(p3) < p1.distance(p2)))
						p2 = p3;
				}
				// move towards closest piper if not too close
				double dist = p1.distance(p2);
				if (dist >= 2.0) {
					dx = (p2.x - p1.x) * rat_speed / dist;
					dy = (p2.y - p1.y) * rat_speed / dist;
					// set new angle
					rat_angle[r] = Math.atan2(dy, dx);
					if (rat_angle[r] < 0.0)
						rat_angle[r] += Math.PI * 2.0;
				}
				// will generate new random angle
				rat_random_angle[r] = true;
			}
			Point p2 = new Point(p1.x + dx, p1.y + dy);
			// east & west collision
			if (Math.abs(p2.x) > inner_side * 0.5) {
				double x = Math.copySign(inner_side * 0.5, p2.x);
				double y = p1.y + (x - p1.x) * (p2.y - p1.y)
				                             / (p2.x - p1.x);
				if (y >= door_length * -0.5 && y <= door_length * 0.5) {
					score[p2.x > 0.0 ? 1 : 3]++;
					rats[r] = null;
					println("Rat caught at (" + x + ", " + y + ")");
					continue;
				}
				dx = Math.copySign(inner_side - Math.abs(p2.x), p2.x) - p1.x;
				rat_angle[r] -= Math.PI * 0.5;
				rat_angle[r] = Math.PI * 2.0 - rat_angle[r];
				rat_angle[r] += Math.PI * 0.5;
				if (rat_angle[r] >= Math.PI * 2.0)
					rat_angle[r] -= Math.PI * 2.0;
				println("Rat collided with vertical wall");
			}
			// north & south collision
			if (Math.abs(p2.y) > inner_side * 0.5) {
				double y = Math.copySign(inner_side * 0.5, p2.y);
				double x = p1.x + (y - p1.y) * (p2.x - p1.x)
				                             / (p2.y - p1.y);
				if (x >= door_length * -0.5 && x <= door_length * 0.5) {
					score[p2.y > 0.0 ? 0 : 2]++;
					rats[r] = null;
					println("Rat caught at (" + x + ", " + y + ")");
					continue;
				}
				dy = Math.copySign(inner_side - Math.abs(p2.y), p2.y) - p1.y;
				rat_angle[r] = Math.PI * 2.0 - rat_angle[r];
				println("Rat collided with horizontal wall");
			}
			// update rat location
			if (dx == 0.0 && dy == 0.0)
				println("Rat is still at (" + p1.x + ", " + p1.y + ")");
			else {
				rats[r] = p2 = new Point(p1.x + dx, p1.y + dy);
				println("Rat moved from (" + p1.x + ", " + p1.y + ")" +
				                   " to (" + p2.x + ", " + p2.y + ")");
			}
		}
		// discard caught rats
		int vr = 0;
		for (int r = 0 ; r != rats.length ; ++r)
			if (rats[r] != null) {
				rat_tune[vr] = rat_tune[r];
				rat_angle[vr] = rat_angle[r];
				rat_random_angle[vr] = rat_random_angle[r];
				rats[vr++] = rats[r];
			}
		rats = Arrays.copyOf(rats, vr);
		// update player positions
		for (int g = 0 ; g != pipers.length ; ++g)
			for (int p = 0 ; p != pipers[g].length ; ++p) {
				double dx = moves[g][p].dx;
				double dy = moves[g][p].dy;
				Point p1 = pipers[g][p];
				Point p2 = new Point(p1.x + dx, p1.y + dy);
				// outer grid east & west
				int side = inner_side + outer_side * 2;
				if (Math.abs(p1.x) <= side * 0.5 &&
				    Math.abs(p2.x) >  side * 0.5) {
					dx = Math.copySign(side - Math.abs(p2.x), p2.x) - p1.x;
					println("Piper collided with vertical grid wall");
				}
				// outer grid north & south
				if (Math.abs(p1.y) <= side * 0.5 &&
				    Math.abs(p2.y) > side * 0.5) {
					dy = Math.copySign(side - Math.abs(p2.y), p2.y) - p1.y;
					println("Piper collided with horizontal grid wall");
				}
				// inner box outwards east & west
				if (Math.abs(p1.x) <= inner_side * 0.5 &&
				    Math.abs(p2.x) >  inner_side * 0.5) {
					double x = Math.copySign(inner_side * 0.5, p2.x);
					double y = p1.y + (x - p1.x) * (p2.y - p1.y)
					                             / (p2.x - p1.x);
					if (y >= door_length * -0.5 && y <= door_length * 0.5)
						println("Piper passed vertical gate from inside"
						        + " (" + x + ", " + y + ")");
					else {
						dx = Math.copySign(inner_side - Math.abs(p2.x), p2.x) - p1.x;
						println("Piper collided with vertical wall from inside");
					}
				}
				// inner box outwards north & south
				if (Math.abs(p1.y) <= inner_side * 0.5 &&
				    Math.abs(p2.y) > inner_side * 0.5) {
					double y = Math.copySign(inner_side * 0.5, p2.y);
					double x = p1.x + (y - p1.y) * (p2.x - p1.x)
					                             / (p2.y - p1.y);
					if (x >= door_length * -0.5 && x <= door_length * 0.5)
						println("Piper passed horizontal gate from inside"
						        + " (" + x + ", " + y + ")");
					else {
						dy = Math.copySign(inner_side - Math.abs(p2.y), p2.y) - p1.y;
						println("Piper collided with horizontal wall from inside");
					}
				}
				// inner box inwards east & west
				if (Math.abs(p1.x) > inner_side * 0.5 &&
				    Math.abs(p2.x) <= inner_side * 0.5) {
					double x = Math.copySign(inner_side * 0.5, p2.x);
					double y = p1.y + (x - p1.x) * (p2.y - p1.y)
					                             / (p2.x - p1.x);
					if (y >= door_length * -0.5 && y <= door_length * 0.5)
						println("Piper passed vertical gate from outside"
						        + " (" + x + ", " + y + ")");
					else {
						dx = Math.copySign(inner_side - Math.abs(p2.x), p2.x) - p1.x;
						if (p1.x + dx == x) dx *= 0.999999;
						println("Piper collided with vertical wall from outside");
					}
				}
				// inner box inwards north & sout
				if (Math.abs(p1.y) > inner_side * 0.5 &&
				    Math.abs(p2.y) <= inner_side * 0.5) {
					double y = Math.copySign(inner_side * 0.5, p2.y);
					double x = p1.x + (y - p1.y) * (p2.x - p1.x)
					                             / (p2.y - p1.y);
					if (x >= door_length * -0.5 && x <= door_length * 0.5)
						println("Piper passed horizontal gate from outside"
						        + " (" + x + ", " + y + ")");
					else {
						dy = Math.copySign(inner_side - Math.abs(p2.y), p2.y) - p1.y;
						if (p1.y + dy == y) dy *= 0.999999;
						println("Piper collided with horizontal wall from outside");
					}
				}
				if (dx == 0.0 && dy == 0.0)
					println("Piper stayed still at (" + p1.x + ", " + p1.y + ")");
				else {
					pipers[g][p] = p2 = new Point(p1.x + dx, p1.y + dy);
					println("Piper moved from (" + p1.x + ", " + p1.y + ")"
					        + " to (" + p2.x + ", " + p2.y + ")");
				}
			}
		// print info on player positions
		for (int g = 0 ; g != pipers.length ; ++g) {
			print("Group " + groups[g] + " ");
			for (int p = 0 ; p != pipers[g].length ; ++p) {
				print("(" + pipers[g][p].x + ", " + pipers[g][p].y + ")");
				if (p + 1 != pipers[g].length) print(", ");
			}
			println(": " + score[g] + " [" + direction[g] + "]");
		}
	}

	// javascript array for piper locations
	private String group_state(int g)
	{
		StringBuffer buf = new StringBuffer();
		double radius = inner_side * 0.5 + outer_side;
		buf.append(groups[g] + ", " + score[g]);
		for (int p = 0 ; p != pipers[g].length ; ++p) {
			double x = pipers[g][p].x / radius;
			double y = pipers[g][p].y / radius;
			int m = moves[g][p] != null && moves[g][p].play ? 1 : 0;
			buf.append(", " + x + ", " + y + ", " + m);
		}
		return buf.toString();
	}

	// javascript array for rat locations
	private String rats_state()
	{
		StringBuffer buf = new StringBuffer();
		double radius = inner_side * 0.5 + outer_side;
		for (int r = 0 ; r != rats.length ; ++r) {
			double x = rats[r].x / radius;
			double y = rats[r].y / radius;
			double a = rat_angle[r];
			int t = rat_tune[r];
			buf.append(x + ", " + y + ", " + a + ", " + t);
			if (r + 1 != rats.length)
				buf.append(", ");
		}
		return buf.toString();
	}

	// serve static files and return dynamic file version
	private int file_server(HTTPServer server) throws IOException
	{
		String path = "";
		for (;;) {
			// get request
			path = server.request();
			while (path == null) {
				System.err.println("Connection error (probably reset)");
				path = server.request();
			}
			println("HTTP request arrived: \"" + path + "\"");
			// check if dynamic content
			if (path.endsWith(".dat")) {
				String num = path.substring(0, path.lastIndexOf('.'));
				try {
					int version = Integer.parseInt(num);
					if (version >= 0) return version;
				} catch (NumberFormatException e) {}
				break;
			}
			// send static content
			if (path.equals("")) path = "webpage.html";
			else if (!path.equals("favicon.ico") &&
			         !path.equals("apple-touch-icon.png") &&
			         !path.equals("script.js")) break;
			File file = new File(root + File.separator + "sim"
			                          + File.separator + path);
			if (server.reply(file))
				println("Static reply: " + file.length() + " bytes");
			else
				System.err.println("Connection failed during reply!");
		}
		throw new UnknownServiceException("Unknown HTTP request: " + path);
	}

	// play game
	private void play(boolean gui) throws IOException
	{
		HTTPServer server = null;
		if (gui) {
			server = new HTTPServer();
			System.err.println("HTTP port: " + server.port());
		}
		int version = 0;
		for (long turn = 0 ;; ++turn) {
			while (gui) {
				// if old version return empty reply
				int req_version = file_server(server);
				if (req_version != 0 && req_version <= version) {
					server.reply("");
					continue;
				}
				// create dynamic content
				double side_ratio = inner_side * 1.0 / outer_side;
				int current_refresh = turn_limit == 0 ? -1 : refresh;
				String content = side_ratio     + "\n"
				               + group_state(0) + "\n"
				               + group_state(1) + "\n"
				               + group_state(2) + "\n"
				               + group_state(3) + "\n"
				               + rats_state()   + "\n"
				               + current_refresh;
				// send dynamic content
				if (!server.reply(content))
					System.err.println("Connection failure during reply!");
				else {
					println("Dynamic reply: " + content.length() + " bytes");
					version = req_version;
					break;
				}
			}
			if (turn_limit == 0) break;
			// run next turn
			if (turn > 0) {
				println("### beg of turn " + turn + " ###");
				next();
				println("### end of turn " + turn + " ###");
				if (turn_limit > 0) turn_limit--;
				if (rats.length == 0) turn_limit = 0;
			}
		}
		if (server != null)
			server.close();
	}

	// the main function
	public void main(String[] args)
	{
		int n_pipers = 2;
		int n_rats = 10;
		boolean gui = false;
		boolean recompile = false;
		groups[0] = groups[1] = groups[2] = groups[3] = "g0";
		direction[0] = "north";
		direction[1] = "east";
		direction[2] = "south";
		direction[3] = "west";
		try {
			for (int a = 0 ; a != args.length ; ++a)
				if (args[a].equals("-p") || args[a].equals("--pipers")) {
					if (++a == args.length)
						throw new Exception("Missing number of pipers");
					n_pipers = Integer.parseInt(args[a]);
					if (n_pipers < 1)
						throw new Exception("Invalid number of pipers (need at least 1)");
				} else if (args[a].equals("-r") || args[a].equals("--rats")) {
					if (++a == args.length)
						throw new Exception("Missing number of rats");
					n_rats = Integer.parseInt(args[a]);
					if (n_rats < 1)
						throw new Exception("Invalid number of rats (need at least 1)");
				} else if (args[a].equals("-t") || args[a].equals("--turns")) {
					if (++a == args.length)
						throw new Exception("Missing turn limit");
					turn_limit = 10 * (long) Integer.parseInt(args[a]);
					if (turn_limit < 0)
						throw new Exception("Invalid turn limit (skip for infinite)");
				} else if (args[a].equals("-s") || args[a].equals("--side")) {
					if (++a == args.length)
						throw new Exception("Missing square side");
					inner_side = Integer.parseInt(args[a]);
					if (inner_side < outer_side)
						throw new Exception("Invalid square side (must exceed outer side)");
				} else if (args[a].equals("--fps")) {
					if (++a == args.length)
						throw new Exception("Missing FPS");
					double fps = Double.parseDouble(args[a]);
					if (fps < 0.0)
						throw new Exception("Invalid FPS (must be non-negative)");
					refresh = fps == 0.0 ? -1 : (int) Math.round(1000.0 / fps);
				} else if (args[a].equals("-g") || args[a].equals("--groups")) {
					if (a + 4 >= args.length)
						throw new Exception("Missing group names");
					groups[0] = args[++a];
					groups[1] = args[++a];
					groups[2] = args[++a];
					groups[3] = args[++a];
				} else if (args[a].equals("-v") || args[a].equals("--verbose"))
					verbose = true;
				else if (args[a].equals("--gui"))
					gui = true;
				else if (args[a].equals("--recompile"))
					recompile = true;
				else throw new Exception("Unknown argument: " + args[a]);
			if (groups == null)
				throw new Exception("Missing group name parameter");
			load(recompile);
		} catch (Exception e) {
			System.err.println("Error during setup: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		// print parameters
		System.err.println("North group: " + groups[0]);
		System.err.println("East  group: " + groups[1]);
		System.err.println("South group: " + groups[2]);
		System.err.println("West  group: " + groups[3]);
		System.err.println("Side: " + inner_side);
		System.err.println("Pipers (per team): " + n_pipers);
		System.err.println("Rats: " + n_rats);
		System.err.println("Turns (max): " + (turn_limit < 0 ? "+oo" : turn_limit));
		System.err.println("Verbose: " + (verbose   ? "yes" : "no"));
		System.err.println("Recompile: " + (recompile ? "yes" : "no"));
		if (!gui)
			System.err.println("GUI: disabled");
		else if (refresh < 0)
			System.err.println("GUI: enabled  (0 FPS)  [reload manually]");
		else if (refresh == 0)
			System.err.println("GUI: enabled  (max FPS)");
		else {
			double fps = 1000.0 / refresh;
			System.err.println("GUI: enabled  (up to " + fps + " FPS)");
		}
		// initialize and play
		if (!init(n_pipers, n_rats)) {
			System.err.println("No valid players to play game");
			System.exit(1);
		}
		try {
			play(gui);
		} catch (Exception e) {
			System.err.println("Error during play: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		// print scores
		System.err.println("North group (" + groups[0] + ") scored: " + score[0]);
		System.err.println("East  group (" + groups[1] + ") scored: " + score[1]);
		System.err.println("South group (" + groups[2] + ") scored: " + score[2]);
		System.err.println("West  group (" + groups[3] + ") scored: " + score[3]);

		// get results
		// format: side,rats,pipers,N,E,S,W,scoreN,scoreE,scoreS,scoreW,winner,total score
		StringBuilder res = new StringBuilder();
		res.append(inner_side
			+ "," + n_rats
			+ "," + n_pipers);

		int totalScore = 0;
		int winner = 5;
		int maxScore = -1;
		for (int i = 0; i < 4; i++) {
			res.append("," + groups[i]);
		}
		for (int i = 0; i < 4; i++) {
			res.append("," + score[i]);
			totalScore += score[i];
			if (score[i] > maxScore) {
				winner = i;
				maxScore = score[i];
			}
		}
		res.append("," + groups[winner]);
		res.append("," + totalScore);
		res.append("\n");
		System.out.println(res);

		// put res into a file
		FileWriter file;
		try {
			file = new FileWriter("pppp/sim/result.csv", true);
			file.write(res.toString());
			file.close();
		} catch (IOException e) {
			System.err.println(e);
		}
	}

	// print after checking verbose parameter
	private void print(String msg)
	{
		if (verbose) System.out.print(msg);
	}

	// print after checking verbose parameter
	private void print(Exception e)
	{
		if (verbose) e.printStackTrace(System.out);
	}

	// print after checking verbose parameter
	private void println(String msg)
	{
		if (verbose) System.out.println(msg);
	}

	// recursive directory scan for files with given extension
	private Set <File> directory(String path, String extension)
	{
		Set <File> files = new HashSet <File> ();
		Set <File> prev_dirs = new HashSet <File> ();
		prev_dirs.add(new File(path));
		do {
			Set <File> next_dirs = new HashSet <File> ();
			for (File dir : prev_dirs)
				for (File file : dir.listFiles())
					if (!file.canRead()) ;
					else if (file.isDirectory())
						next_dirs.add(file);
					else if (file.getPath().endsWith(extension))
						files.add(file);
			prev_dirs = next_dirs;
		} while (!prev_dirs.isEmpty());
		return files;
	}

	// compile and load source files
	private void load(boolean compile) throws IOException,
	                                ReflectiveOperationException
	{
		// get unique player sources
		Map <String, Class> group_map = new HashMap <String, Class> ();
		for (int g = 0 ; g != groups.length ; ++g)
			group_map.put(groups[g], null);
		// compile and load classes
		ClassLoader loader = Simulator.class.getClassLoader();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		StandardJavaFileManager manager = compiler.
		                        getStandardFileManager(null, null, null);
		String sep = File.separator;
		for (String group : group_map.keySet()) {
			String dir = root + sep + group;
			File class_file = new File(dir + sep + "Player.class");
			if (compile || !class_file.exists()) {
				File source_file = new File(dir + sep + "Player.java");
				if (!source_file.exists())
					throw new FileNotFoundException(
					          "Missing source of group " + group);
				Set <File> files = directory(dir, ".java");
				System.err.print("Compiling " + group +
				                 " (" + files.size() + " files) ... ");
				if (!compiler.getTask(null, manager, null, null, null,
				     manager.getJavaFileObjectsFromFiles(files)).call())
					throw new IOException(
					          "Cannot compile source of " + group);
				System.err.println("done!");
				class_file = new File(dir + sep + "Player.class");
				if (!class_file.exists())
					throw new FileNotFoundException(
					          "Missing class of group " + group);
			}
			Class player = loader.loadClass(root + "." + group + ".Player");
			group_map.replace(group, player);
		}
		// map to players
		for (int g = 0 ; g != groups.length ; ++g)
			player_classes[g] = group_map.get(groups[g]);
	}
}
