package fr.lelouet.choco.limitpower.model;

/**
 * HPC or batch application, supposed to work a given amount of time.
 * 
 * @author Guillaume Le Louët [guillaume.lelouet@gmail.com] 2015
 *
 */
public class HPC {

	/**
	 * the first moment we can start the application
	 */
	public int start;

	/**
	 * number of different time intervals the application must run to be fulfiled.
	 */
	public int duration;

	/**
	 * power consumption of the application when running
	 */
	public int power;

	/**
	 * benefit added from completing the application in time (ie scheduling all
	 * its parts before the deadline)
	 */
	public int profit;

	/**
	 * time after which the application must not have any remaining part scheduled
	 * in order to consider the benefit acquired.
	 */
	public int deadline;

	public HPC(int start, int duration, int power, int profit, int deadline) {
		this.start = start;
		this.duration = duration;
		this.power = power;
		this.profit = profit;
		this.deadline = deadline;
	}

	public HPC deadline(int deadline) {
		this.deadline = deadline;
		return this;
	}

	public HPC duration(int duration) {
		this.duration = duration;
		return this;
	}

	public HPC power(int power) {
		this.power = power;
		return this;
	}

	public HPC profit(int profit) {
		this.profit = profit;
		return this;
	}

	public HPC start(int start) {
		this.start = start;
		return this;
	}

	@Override
	public String toString() {
		return "HPC(ddl=" + deadline + ",dur=" + duration + ",pwr=" + power + ",pft=" + profit + ",stt=" + start + ")";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != HPC.class)
			return false;
		HPC other = (HPC) obj;

		return other.deadline == deadline && other.duration == duration && other.power == power && other.profit == profit
				&& other.start == start;
	}

}
