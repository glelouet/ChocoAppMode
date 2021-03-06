package fr.emn.premode;

import java.util.HashMap;
import java.util.List;

import fr.emn.premode.center.PowerMode;

public class Planning {

	public Planning() {
	}

	public HashMap<String, List<PowerMode>> webModes = new HashMap<>();

	public HashMap<String, List<Integer>> hpcStarts = new HashMap<>();

	/** for each application name, at each intervale slot the application node */
	public HashMap<String, List<String>> appHosters = new HashMap<>();

	public int profit;

	@Override
	public String toString() {
		return "" + profit + webModes + hpcStarts + appHosters;
	}

	public long buildMS = -1;

	public long stratMS = -1;

	public long searchMS = -1;

	public long extrMS = -1;

}
