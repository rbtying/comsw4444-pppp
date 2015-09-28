package pppp.sim;

import pppp.sim.Simulator1;
import java.io.*;

public class Test {
	public static void main(String[] args) {
		int side = 100;
		int rats = 20;
		int pipers = 2;
		int trials = 5; // number of trials
		int[] groups = {3, 4, 6, 8, 9};

		for (int i = 0; i < groups.length - 2; i++) {
			for (int j = i + 1; j < groups.length - 1; j++) {
				for (int k = j + 1; k < groups.length; k++) {
					String config = "-g g7 g" + groups[i] + " g" + groups[j] + " g" + groups[k]
									+ " -s " + side
									+ " -r " + rats
									+ " -p " + pipers;
					// System.out.println(config);
					for (int round = 0; round < 5; round++) {
						Simulator1 sim = new Simulator1();
						sim.main(config.split(" "));
					}
					try {
						FileWriter file = new FileWriter("pppp/sim/result.csv", true);
						file.write("\n");
						file.close();
					} catch (IOException e) {
						System.err.println(e);
					}
				}
			}
		}
	}
}