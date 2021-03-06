
package fr.emn.premode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.ParallelPortfolio;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.solver.variables.Task;
import org.chocosolver.util.ESat;

import fr.emn.premode.center.HPC;
import fr.emn.premode.center.PowerMode;
import fr.emn.premode.center.SchedulingProblem;
import fr.emn.premode.heuristics.Heuristic;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

/**
 * solve an app scheduling problem with benefit maximisation.
 * <ul>
 * <li>the time is divided into a finite number of slots ; interval 0 is from slot 0 to slot 1, etc. we have
 * {@link #nbIntervals} interval so the times go from 0 to {@link #nbIntervals} included (this is last slot's end time)
 * </li>
 * <li>web apps start on 0 and end on {@link #nbIntervals}; have several modes, each consume an amount of power and give
 * a profit</li>
 * <li>hpc app have a given amount of subtask to schedule sequentially on the intervals,each of duration 1, each
 * consumming the same amount of power ; The benefit is given if the hpc task is scheduled entirely before its deadline
 * expires, meaning its last subtask.end <= deadline</li>
 * <li>reduction amounts specify reduction in the total power capacity</li>
 * </ul>
 *
 * @author Guillaume Le Louët [guillaume.lelouet@gmail.com] 2015
 */
public class Scheduler extends Model {

	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Scheduler.class);

	protected SchedulingProblem source = new SchedulingProblem();

	public SchedulingProblem getSource() {
		return source;
	}

	protected boolean showContradictions = false;

	public Scheduler withShowContradictions(boolean showContradictions) {
		this.showContradictions = showContradictions;
		return this;
	}

	protected boolean showDecisions = false;

	public Scheduler withShowDecisions(boolean showDecisions) {
		this.showDecisions = showDecisions;
		return this;
	}

	protected boolean showSolutions = false;

	public Scheduler withShowSolutions(boolean showSolutions) {
		this.showSolutions = showSolutions;
		return this;
	}

	protected long timeLimit = -1;

	public Scheduler withTimeLimit(long ms) {
		timeLimit = ms;
		return this;
	}

	protected IntVar fixed(int i) {
		return intVar(i);
	}

	protected IntVar enumerated(String name, int min, int max) {
		return intVar(name, min, max, false);
	}

	protected IntVar bounded(String name, int min, int max) {
		return intVar(name, min, max, true);
	}

	//////////////////////////////////////////////////////////////////////////
	// identification of tasks by their index

	/////
	// name<->idx for apps
	////

	protected TObjectIntMap<String> appName2Index = new TObjectIntHashMap<>(10, 0.5f, -1);
	protected String[] index2AppName = null;

	////
	// name<->idx for servers
	////

	protected TObjectIntMap<String> servName2Index = new TObjectIntHashMap<>();
	protected String[] index2ServName = null;

	/**
	 * fill data in {@link #servName2Index}, {@link #index2ServName}, {@link #appName2Index}, {@link #index2AppName}
	 */
	protected void affectIndexes() {
		List<String> appnames = source.appNames().collect(Collectors.toList());
		index2AppName = appnames.toArray(new String[] {});
		for (int i = 0; i < index2AppName.length; i++) {
			appName2Index.put(index2AppName[i], i);
		}
		index2ServName = source.servNames().collect(Collectors.toList()).toArray(new String[] {});
		for (int i = 0; i < index2ServName.length; i++) {
			servName2Index.put(index2ServName[i], i);
		}
	}

	public int app(String appName) {
		return appName2Index.get(appName);
	}

	public String app(int appIdx) {
		if (appIdx < 0 || appIdx >= index2AppName.length) {
			return null;
		}
		return index2AppName[appIdx];
	}

	public int serv(String servName) {
		return servName2Index.get(servName);
	}

	public String serv(int servIdx) {
		if (servIdx < 0 || servIdx >= index2ServName.length) {
			return null;
		}
		return index2ServName[servIdx];
	}

	//////////////////////////////////////////////////////////////////////

	/**
	 * positions[i][j] is the position at interval i of the application j . if it is -1 (for an hpc app) then the
	 * application is not run at interval i.
	 */
	protected IntVar[][] appPositions;

	/**
	 * make the variables related to the position of the applications. The variables are not constrained yet.<br />
	 * If web apps don't need any specific constraint, the hpc apps must have NO host (value -1) when no subtask is
	 * executed on given interval.
	 */
	protected void makeAppPositions() {
		appPositions = new IntVar[source.nbIntervals][index2AppName.length];
		for (int appIdx = 0; appIdx < index2AppName.length; appIdx++) {
			String appName = index2AppName[appIdx];
			boolean isWeb = !source.getWebPowerModes(appName).isEmpty();
			BoolVar[] executions = null;
			if (!isWeb) {
				executions = new BoolVar[source.nbIntervals];
				hpcExecuteds.put(appName, executions);
			}
			for (int itv = 0; itv < appPositions.length; itv++) {
				IntVar position = intVar("appPos_" + itv + "_" + appIdx, isWeb ? 0 : -1, source.nbServers() - 1);
				appPositions[itv][appIdx] = position;
				if (!isWeb) {
					BoolVar executed = boolVar("appExec_" + itv + "_" + appIdx);
					executions[itv] = executed;
					arithm(position, ">=", 0).reifyWith(executed);
				}
			}
		}
	}

	public IntVar position(int itvIdx, int appIdx) {
		return appPositions[itvIdx][appIdx];
	}

	/**
	 * ismigrateds[i][j] is true if app j is moved at interval i. first interval always returns false. a web app is
	 * migrated if hoster changed, a hpc app is migrated if hoster was not -1 and hoster changed.
	 */
	protected BoolVar[][] isMigrateds = null;

	protected void makeIsMigrateds() {
		isMigrateds = new BoolVar[source.nbIntervals][index2AppName.length];
		for (int appIdx = 0; appIdx < index2AppName.length; appIdx++) {
			String prevHosName = source.previous.pos.get(index2AppName);
			if (prevHosName != null && servName2Index.containsKey(prevHosName)) {
				isMigrateds[0][appIdx] = boolVar("appMig_" + 0 + "_" + appIdx);
				int prevIdx = servName2Index.get(prevHosName);
				arithm(appPositions[0][appIdx], "!=", prevIdx).reifyWith(isMigrateds[0][appIdx]);
			} else {
				isMigrateds[0][appIdx] = boolVar(false);
			}
			String appName = index2AppName[appIdx];
			boolean isWeb = source.webPowers(appName) != null;
			for (int itv = 1; itv < source.nbIntervals; itv++) {
				isMigrateds[itv][appIdx] = boolVar("appMig_" + itv + "_" + appIdx);
				if (isWeb) {
					arithm(appPositions[itv][appIdx], "!=", appPositions[itv - 1][appIdx]).reifyWith(isMigrateds[itv][appIdx]);
				} else {
					BoolVar moved = boolVar("appMoved_" + itv + "_" + appIdx);
					arithm(appPositions[itv][appIdx], "!=", appPositions[itv - 1][appIdx]).reifyWith(moved);
					and(hpcExecuteds.get(appName)[itv - 1], moved, hpcExecuteds.get(appName)[itv])
					.reifyWith(isMigrateds[itv][appIdx]);
				}
			}
		}
	}

	// migrationCosts[i] is the cost of vm migration at interval i
	protected IntVar[] migrationCosts = null;

	protected void makeMigrationCosts() {
		migrationCosts = new IntVar[source.nbIntervals];
		for (int itv = 0; itv < migrationCosts.length; itv++) {
			IntVar[] coefs = new IntVar[index2AppName.length];
			for (int i = 0; i < coefs.length; i++) {
				coefs[i] = intVar(source.migrateCost(index2AppName[i]));
			}
			IntVar[] appCost = new IntVar[index2AppName.length];
			int maxTotalCost = 0;
			for (int appIdx = 0; appIdx < appCost.length; appIdx++) {
				int maxCost = coefs[appIdx].getUB();
				appCost[appIdx] = intVar("appcost_" + itv + "_" + appIdx, 0, maxCost);
				maxTotalCost += maxCost;
				times(isMigrateds[itv][appIdx], coefs[appIdx], appCost[appIdx]).post();
			}
			migrationCosts[itv] = intVar("migrationCost_" + itv, 0, maxTotalCost);
			sum(appCost, "=", migrationCosts[itv]).post();
		}
	}

	///////////////////////////////////////////
	// cumulative tasks

	/**
	 * all tasks for cumulative : web, hpc, and specific power-limit-oriented tasks<br />
	 * task at position i has power at position i in {@link #allPowers}
	 */
	protected List<Task> allTasks = new ArrayList<>();

	protected List<IntVar> allPowers = new ArrayList<>();

	/**
	 * @param t
	 *          a task
	 * @param p
	 *          the power variable of the task
	 */
	protected void addTask(Task t, IntVar p) {
		allTasks.add(t);
		allPowers.add(p);
	}

	// all the non-null profits of the tasks we schedule

	protected List<IntVar> allProfits = new ArrayList<>();

	protected IntVar totalProfit;

	public IntVar getProfit() {
		makeCumulative();
		return totalProfit;
	}

	//
	// web tasks
	//

	public class WebSubClass extends Task {

		public final String name;

		public final int[] profits;
		public final int[] powers;

		public final IntVar mode, power, profit;

		WebSubClass(String name, int start, int[] profits, int[] powers) {
			super(fixed(start), fixed(1), fixed(start + 1));
			assert profits.length == powers.length;
			this.name = name;
			this.profits = profits;
			this.powers = powers;
			mode = enumerated(name + "_mode", 0, powers.length - 1);
			power = enumerated(name + "_power", Scheduler.minIntArray(powers), Scheduler.maxIntArray(powers));
			post(element(power, powers, mode));
			profit = enumerated(name + "_cost", Scheduler.minIntArray(profits), Scheduler.maxIntArray(profits));
			post(element(profit, profits, mode));
		}

		@Override
		public String toString() {
			return "webTask(start=" + getStart() + ")";
		}
	}

	/**
	 * for each web application, its modes
	 */
	public Map<Integer, List<WebSubClass>> webModes = new HashMap<>();

	/** make one task corresponding to the web apps */
	protected void makeWebTasks() {
		source.webNames().forEach(name -> {
			int[] appprofits = source.webProfits(name);
			int[] apppower = source.webPowers(name);
			List<WebSubClass> l = new ArrayList<>();
			webModes.put(app(name), l);
			for (int i = 0; i < source.nbIntervals; i++) {
				WebSubClass t = new WebSubClass(name + "_" + i, i, appprofits, apppower);
				l.add(t);
				addTask(t, t.power);
				allProfits.add(t.profit);
			}
		});
	}

	//
	// HPC tasks
	//

	/**
	 * an HPC task is divided in subtasks. each subtask has a length of one and must be started after the previous task.
	 * The benefit of the first subtasks is 0, the benefit of the last subtasks is its benefit if it is scheduled before
	 * its deadline
	 *
	 * @author Guillaume Le Louët
	 */
	public class HPCSubTask extends Task {

		HPC master;

		BoolVar onSchedule;

		public BoolVar getOnSchedule() {
			return onSchedule;
		}

		/** the height of the task */
		IntVar power;

		public IntVar getPower() {
			return power;
		}

		public HPCSubTask(HPC master, IntVar start, IntVar end, IntVar power, BoolVar onSchedule) {
			super(start, fixed(1), end);
			this.master = master;
			this.power = power;
			this.onSchedule = onSchedule;
		}
	}

	/** hpc name -> list of subtasks of this app. each subtask ha a duration of 1 */
	protected HashMap<String, List<HPCSubTask>> hpcTasks = new HashMap<>();

	public List<HPCSubTask> getHPCTasks(String name) {
		return hpcTasks.get(name);
	}

	/** hpc name-> interval-> is the hpc scheduled on this interval? */
	protected HashMap<String, BoolVar[]> hpcExecuteds = new HashMap<>();

	/**
	 * create a variable related to an existing interval.<br />
	 * This variable can take a value from 0 to the number of intervals +1 {@link SchedulingProblem#nbIntervals}
	 */
	protected IntVar makeTimeSlotVar(String name) {
		return bounded(name, 0, source.nbIntervals + 1);
	}

	/** for each hpc task, the set of intervals it runs at */
	protected HashMap<String, SetVar> hpcIntervals = new HashMap<>();

	protected void makeHPCTasks() {
		int[] allIntervals = new int[source.nbIntervals + 1];
		for (int i = 0; i < allIntervals.length; i++) {
			allIntervals[i] = i;
		}
		source.hpcNames().forEach(hpcName -> {
			HPC h = source.getHPC(hpcName);
			ArrayList<HPCSubTask> subtasksList = new ArrayList<>();
			hpcTasks.put(hpcName, subtasksList);
			HPCSubTask last = null;
			IntVar[] hpcstarts = new IntVar[h.duration];
			for (int i = 0; i < h.duration; i++) {
				String subTaskName = hpcName + "_" + i;
				IntVar start = makeTimeSlotVar(subTaskName + "_start");
				IntVar end = makeTimeSlotVar(subTaskName + "_end");
				BoolVar onSchedule = boolVar(subTaskName + "_onschedule");
				arithm(end, "<=", h.deadline > 0 ? Math.min(h.deadline, source.nbIntervals) : source.nbIntervals)
				.reifyWith(onSchedule);
				IntVar power = enumerated(subTaskName + "_power", 0, h.power);
				post(element(power, new int[] { 0, h.power }, onSchedule));
				if (last != null) {
					arithm(last.getStart(), "<=", start).post();
					Constraint orderedCstr = arithm(last.getEnd(), "<=", start);
					BoolVar isOrdered = boolVar(subTaskName + "_ordered");
					orderedCstr.reifyWith(isOrdered);
					arithm(onSchedule, "<=", isOrdered).post();
				} else {
					arithm(intVar(h.start), "<=", start).post();
				}
				HPCSubTask t = new HPCSubTask(h, start, end, power, onSchedule);
				subtasksList.add(t);
				last = t;
				addTask(t, power);
				hpcstarts[i] = start;
			}
			IntVar profit = enumerated(hpcName + "_profit", 0, h.profit);
			allProfits.add(profit);
			post(element(profit, new int[] { 0, h.profit }, last.onSchedule));

			SetVar usedItvs = setVar(hpcName + "_itv", new int[] {}, allIntervals);
			union(hpcstarts, usedItvs).post();
			hpcIntervals.put(hpcName, usedItvs);
			BoolVar[] executions = hpcExecuteds.get(hpcName);
			assert executions != null : "hpc " + hpcName + " has null table of executions";
			for (int itv = 0; itv < appPositions.length; itv++) {
				Constraint memb = member(intVar(itv), usedItvs);
				memb.reifyWith(executions[itv]);
			}
		});
	}

	////////////////////////////////////////////////////////////////////////////

	//
	// reduction tasks to reduce effective power at a given time
	//

	/** maxpower used at any given interval, sum of servers max powers. Can still be reduced by power limits */
	int maxPower = -1;

	public int getMaxPower() {
		return maxPower;
	}

	/**
	 * powers[i][j] is the power used at interval i by application j
	 */
	protected IntVar[][] appPowers;

	/**
	 * servPowers[i][j] is at interval i power of server j
	 */
	protected IntVar[][] servPowers;

	/**
	 * stores the power of actual web applications, hpc tasks in an array ; also add the servers-related powers.<br />
	 * to be called after {@link #makeHPCTasks()} and {@link #makeWebTasks()}. <br />
	 * the web powers are retrieved from the corresponding {@link WebSubClass} ; <br />
	 * the hpc powers are created, and constrained to 0 if no {@link HPCSubTask} is scheduled on the interval or the hpc
	 * power if one is scheduled.
	 */
	protected void makeAppPowers() {
		maxPower = source.servers().mapToInt(e -> e.getValue().maxPower).sum();
		appPowers = new IntVar[source.nbIntervals][];
		for (int i = 0; i < appPowers.length; i++) {
			appPowers[i] = new IntVar[index2AppName.length];
		}
		// powers of each web tasks, retrieved from the power of the cumulative
		// tasks
		for (Entry<Integer, List<WebSubClass>> e : webModes.entrySet()) {
			int widx = e.getKey();
			IntVar[] wpowers = e.getValue().stream().map(websubtask -> websubtask.power).collect(Collectors.toList())
					.toArray(new IntVar[] {});
			for (int itv = 0; itv < appPowers.length; itv++) {
				appPowers[itv][widx] = wpowers[itv];
			}
		}
		// power of each hpc task, 0 if the task is not on schedule.
		for (Entry<String, List<HPCSubTask>> e : hpcTasks.entrySet()) {
			int hidx = appName2Index.get(e.getKey());
			int power = source.getHPC(e.getKey()).power;
			BoolVar[] executions = hpcExecuteds.get(e.getKey());
			for (int itv = 0; itv < appPowers.length; itv++) {
				appPowers[itv][hidx] = intScaleView(executions[itv], power);
			}
		}
		// now make all the [interval][server] power variables.
		servPowers = new IntVar[source.nbIntervals][index2ServName.length];
		for (int servIdx = 0; servIdx < index2ServName.length; servIdx++) {
			int maxpower = source.server(index2ServName[servIdx]).maxPower;
			for (int itv = 0; itv < source.nbIntervals; itv++) {
				IntVar[] powerOnServers = new IntVar[index2AppName.length];
				for (int appIdx = 0; appIdx < powerOnServers.length; appIdx++) {
					IntVar power = intVar("appPwrOn_" + itv + "_" + appIdx + "_" + servIdx, 0, maxpower);
					powerOnServers[appIdx] = power;
					BoolVar execution = boolVar("appExecOn_" + itv + "_" + appIdx + "_" + servIdx);
					arithm(appPositions[itv][appIdx], "=", servIdx).reifyWith(execution);
					times(execution, appPowers[itv][appIdx], power).post();
				}
				IntVar servPower = intVar("servpower_" + itv + "_" + servIdx, 0, maxpower);
				servPowers[itv][servIdx] = servPower;
				sum(powerOnServers, "=", servPower).post();
			}
		}
	}

	/**
	 * add fake tasks that reduce the total power available on each interval
	 */
	protected void makeReductionTasks() {
		for (int i = 0; i < source.nbIntervals; i++) {
			int limit = source.getPower(i);
			// if we have a limit, we must reduce the power a this interval. anyhow, we also must consider the cost of
			// migrating the vms
			addTask(new Task(fixed(i), fixed(1), fixed(i + 1)),
					limit != -1 && limit < maxPower ? intOffsetView(migrationCosts[i], maxPower - limit) : migrationCosts[i]);
		}
	}

	//
	// the cumulative constraint on total power.
	//

	/** add the profits and the powers of the tasks */
	protected void makeCumulative() {
		if (totalProfit != null) {
			return;
		}
		totalProfit = bounded("totalProfit", 0, source.getMaxProfit());
		post(sum(allProfits.toArray(new IntVar[] {}), "=", totalProfit));
		Task[] tasks = allTasks.toArray(new Task[] {});
		IntVar[] heights = allPowers.toArray(new IntVar[] {});
		post(cumulative(tasks, heights, fixed(maxPower)));
	}

	//
	// pack the resources
	//

	// resources->interval->servderIdx->use
	public HashMap<String, IntVar[][]> resourceServersUse = new HashMap<>();

	protected void makePackings() {
		source.resources().forEach(e -> {
			IntVar[][] serversUses = new IntVar[source.nbIntervals][];
			String resName = e.getKey();
			resourceServersUse.put(resName, serversUses);
			ToIntFunction<String> res = e.getValue();
			int[] serversCapas = new int[index2ServName.length + 1];
			serversCapas[0] = Integer.MAX_VALUE - 1;
			for (int i = 1; i < serversCapas.length; i++) {
				serversCapas[i] = res.applyAsInt(index2ServName[i - 1]);
			}
			int[] appuse = new int[index2AppName.length];
			for (int i = 0; i < index2AppName.length; i++) {
				appuse[i] = res.applyAsInt(index2AppName[i]);
			}
			for (int itv = 0; itv < source.nbIntervals; itv++) {
				IntVar[] serversLoads = new IntVar[serversCapas.length];
				for (int servIdx = 0; servIdx < serversLoads.length; servIdx++) {
					serversLoads[servIdx] = intVar(resName + "_serverload_" + itv + "_" + (servIdx - 1), 0, serversCapas[servIdx]);
				}
				serversUses[itv] = serversLoads;
				binPacking(appPositions[itv], appuse, serversLoads, -1).post();
			}
		});
	}
	//
	// define objective
	//

	/**
	 * create a variable which is the sum of extracted values from HPC subtasks
	 *
	 * @param name
	 *          the name of the variable
	 * @param getter
	 *          the function to get the intvar from hpc subtasks
	 * @param added
	 *          IntVar to add to that sum, or null to add zero.
	 * @return a new variable constrained to the sum of the intVar of the HPCSubtasks + added if not null.
	 */
	public IntVar makeSubtaskSum(String name, Function<HPCSubTask, IntVar> getter, IntVar added) {
		IntVar ret = bounded(name, 0, IntVar.MAX_INT_BOUND);
		// stream of the hpcsubtasks variables
		Stream<IntVar> variables = hpcTasks.values().stream().flatMap(List::stream).map(getter);
		List<IntVar> vars = (added == null ? variables : Stream.concat(variables, Stream.of(added)))
				.collect(Collectors.toList());
		sum(vars.toArray(new IntVar[] {}), "=", ret).post();
		return ret;
	}

	/**
	 * clear the caches and creates variables and constraints linked to a problem.
	 *
	 * @param source
	 *          the model of the problem to solve
	 * @return this.
	 */
	public Scheduler withVars(SchedulingProblem source) {
		clearCache();
		this.source = source;
		// first we create indexes for the servers and the applications
		affectIndexes();
		// then we create position variables for each interval and each app
		makeAppPositions();
		// we make migration cost at each interval : a VM is migrated if was running
		// at previous interval and present interval is different from previous
		makeIsMigrateds();
		makeMigrationCosts();

		makeWebTasks();
		makeHPCTasks();
		makeAppPowers();
		makeReductionTasks();
		makeCumulative();
		makePackings();
		return this;
	}

	//
	// extract data to a result
	//

	protected Planning extractResult(Solution s) {
		Planning ret = new Planning();
		for (Entry<Integer, List<WebSubClass>> e : webModes.entrySet()) {
			String name = app(e.getKey());
			ArrayList<PowerMode> list = new ArrayList<>();
			List<PowerMode> modes = source.getWebPowerModes(name);
			for (WebSubClass w : e.getValue()) {
				list.add(modes.get(s.getIntVal(w.mode)));
			}
			ret.webModes.put(name, list);
		}
		for (Entry<String, List<HPCSubTask>> e : hpcTasks.entrySet()) {
			List<Integer> l = new ArrayList<>();
			ret.hpcStarts.put(e.getKey(), l);
			for (HPCSubTask h : e.getValue()) {
				if (s.getIntVal(h.onSchedule) == 1) {
					l.add(s.getIntVal(h.getStart()));
				}
			}
		}
		ret.profit = s.getIntVal(totalProfit);
		for (int appIdx = 0; appIdx < index2AppName.length; appIdx++) {
			String appName = index2AppName[appIdx];
			List<String> positionsl = new ArrayList<>();
			for (IntVar[] appPosition : appPositions) {
				IntVar posVar = appPosition[appIdx];
				int positionIdx = s.getIntVal(posVar);
				positionsl.add(positionIdx == -1 ? null : index2ServName[positionIdx]);
			}
			ret.appHosters.put(appName, positionsl);
		}
		return ret;

	}

	protected void clearCache() {
		allPowers.clear();
		allProfits.clear();
		allTasks.clear();
		appName2Index.clear();
		appPowers = null;
		hpcExecuteds.clear();
		hpcIntervals.clear();
		hpcTasks.clear();
		index2AppName = null;
		index2ServName = null;
		isMigrateds = null;
		migrationCosts = null;
		appPositions = null;
		servName2Index.clear();
		servPowers = null;
		totalProfit = null;
		webModes.clear();
	}

	//
	// Solve the problem
	//

	public Planning solve(SchedulingProblem m) {
		long startTime = System.currentTimeMillis();
		withVars(m);
		long buildEnd = System.currentTimeMillis();
		long stratEnd = -1, searchEnd = -1;
		IntVar obj = source.objective == null ? null : source.objective.getObjective(this);
		setObjective(true, obj);
		Heuristic[] hMakers = source.objective.getStrategies();
		Planning ret;
		if (hMakers == null || hMakers.length <= 1) {
			if (showContradictions) {
				getSolver().showContradiction();
			}
			if (showDecisions) {
				getSolver().showDecisions();
			}
			if (showSolutions) {
				getSolver().showSolutions();
			}
			if (hMakers != null) {
				getSolver().setSearch(hMakers[0].makeStrat(this));
			}
			if (timeLimit > 0) {
				getSolver().limitTime(timeLimit);
			}
			stratEnd = System.currentTimeMillis();
			Solution s = new Solution(this);
			while (getSolver().solve()) {
				s.record();
			}
			searchEnd = System.currentTimeMillis();
			ret = getSolver().isFeasible() == ESat.TRUE ? extractResult(s) : null;
		} else {
			ParallelPortfolio pares = new ParallelPortfolio(false);
			for (Heuristic hMaker : hMakers) {
				// first solver is this, next solvers are created
				Scheduler other = hMaker == hMakers[0] ? this : new Scheduler().withVars(getSource());
				if (showContradictions) {
					other.getSolver().showContradiction();
				}
				if (showDecisions) {
					other.getSolver().showDecisions();
				}
				if (showSolutions) {
					other.getSolver().showSolutions();
				}
				other.getSolver().setSearch(hMaker.makeStrat(other));
				pares.addModel(other);
			}

			Solution s = null;
			while (pares.solve()) {
				s= new Solution(
						pares.getBestModel());
				s.record();
			}
			ret = s != null ? extractResult(s) : null;
		}
		if (ret != null) {
			ret.buildMS = buildEnd - startTime;
			ret.stratMS = stratEnd - buildEnd;
			ret.searchMS = searchEnd - stratEnd;
			ret.extrMS = System.currentTimeMillis() - searchEnd;
		}
		return ret;
	}

	public static int maxIntArray(int... vals) {
		if (vals == null || vals.length == 0) {
			throw new UnsupportedOperationException();
		}
		int ret = vals[0];
		for (int i : vals) {
			ret = Math.max(ret, i);
		}
		return ret;
	}

	public static int minIntArray(int... vals) {
		if (vals == null || vals.length == 0) {
			throw new UnsupportedOperationException();
		}
		int ret = vals[0];
		for (int i : vals) {
			ret = Math.min(ret, i);
		}
		return ret;
	}

	public static Planning solv(SchedulingProblem m) {
		return new Scheduler().solve(m);
	}

	public static Planning debugSolv(SchedulingProblem m) {
		return new Scheduler().withShowContradictions(true).solve(m);
	}

}
