/**
 *
 */
package fr.emn.premode.heuristics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.search.strategy.strategy.IntStrategy;
import org.chocosolver.solver.search.strategy.strategy.StrategiesSequencer;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.emn.premode.Scheduler;
import fr.emn.premode.Scheduler.HPCSubTask;
import fr.emn.premode.Scheduler.WebSubClass;

/**
 * set the modes of the web apps to the highest profit.
 *
 * @author Guillaume Le Louët [guillaume.lelouet@gmail.com] 2016
 */
public class HeuristicsHelper {

	private static final Logger logger = LoggerFactory.getLogger(HeuristicsHelper.class);

	public static final Heuristic DEFAULT_HEURISTIC = Search::defaultSearch;

	/**
	 * set the mode of webs to the lowest power use
	 *
	 * @param scheduler
	 * @return
	 */
	public static IntStrategy webLowPower(Scheduler scheduler) {

		return Search.inputOrderLBSearch(scheduler.webModes.values().stream().flatMap(List::stream).map(wsc -> wsc.power)
				.collect(Collectors.toList()).toArray(new IntVar[] {}));
	}

	public static AbstractStrategy<?> assignWebProfitThenServer(Scheduler sc, IntVar profit, IntVar host,
			int[] hostOrder) {
		return Search.sequencer(Search.inputOrderUBSearch(profit), Search.intVarSearch(v -> {
			for (int i : hostOrder) {
				if (host.contains(i)) {
					return host;
				}
			}
			return null;
		}, h -> {
			for (int i : hostOrder) {
				if (host.contains(i)) {
					return i;
				}
			}
			HeuristicsHelper.logger.warn("wtf " + host);
			return host.getLB();
		}, new IntVar[] { host }));
	}

	/**
	 * sort the web apps indexes by the max profit of the web app
	 *
	 * @param as
	 * @return
	 */
	public static int[] sortWebAppsByMaxProfit(Scheduler as) {
		// map web idx to maxprofit
		HashMap<Integer, Integer> idx2MaxProfit = new HashMap<>();
		as.getSource().webNames().forEach(name -> {
			int maxprofit = as.getSource().getWebPowerModes(name).stream().mapToInt(pm -> pm.profit).max().getAsInt();
			idx2MaxProfit.put(as.app(name), maxprofit);
		});
		// sort the map entries by decreasing value
		return idx2MaxProfit.entrySet().stream().sorted((e1, e2) -> e2.getValue() - e1.getValue()).mapToInt(e -> e.getKey())
				.toArray();
	}

	/**
	 * sort the hpc apps indexes by their interest, as profit/weight
	 *
	 * @param as
	 *          the scheduler to get the
	 * @param weight
	 *          the weight of an app name
	 * @return a new int[]
	 */
	public static int[] sortHpcAppsByInterest(Scheduler as, ToIntFunction<String> weight) {
		// map hpc idx to interest
		HashMap<Integer, Integer> idx2MaxProfit = new HashMap<>();
		as.getSource().hpcNames().forEach(name -> {
			// multiply by 100 to avoid truncating precision
			int interest = 100 * as.getSource().getHPC(name).profit / weight.applyAsInt(name);
			idx2MaxProfit.put(as.app(name), interest);
		});
		// sort the map entries by decreasing value
		return idx2MaxProfit.entrySet().stream().sorted((e1, e2) -> e2.getValue() - e1.getValue()).mapToInt(e -> e.getKey())
				.toArray();
	}

	/**
	 * create a strategies which set the mode of the web apps to highest. The apps are selected by maximum profit first.
	 *
	 * @param as
	 * @return a new strategy setting the web apps to lowest power mode.
	 */
	public static AbstractStrategy<?> affectWebLowPower(Scheduler as) {
		IntVar[] webpowers = as.webModes.values().stream().flatMap(l -> l.stream()).map(wsc -> wsc.power)
				.toArray(IntVar[]::new);
		return Search.inputOrderLBSearch(webpowers);
	}

	/**
	 * set the web tasks to their highest profit
	 */
	public static final Heuristic WEB_HIGH_PROFIT = WebHighestProfitFirst.INSTANCE.chain(DEFAULT_HEURISTIC);

	/**
	 * sort the server index by their remaining resource in the scheduler source, decreasing
	 *
	 * @param as
	 *          scheduler
	 * @param resName
	 *          name of the resource, which should be present in the scehduler
	 * @return a new int[]
	 */
	public static int[] sortServersByRemaining(Scheduler as, String resName) {
		// from the name we get the resource
		ToIntFunction<String> resource = as.getSource().getResource(resName);
		// sever to remaining
		HashMap<Integer, Integer> remaining = new HashMap<>();
		for (int servIdx = 0; servIdx < as.getSource().nbServers(); servIdx++) {
			remaining.put(servIdx, resource.applyAsInt(as.serv(servIdx)));
		}
		// then order the entries
		return remaining.entrySet().stream().sorted((e1, e2) -> e1.getValue() - e2.getValue()).mapToInt(Entry::getKey)
				.toArray();
	}

	public static AbstractStrategy<?> assignWebProfitThenServer(Scheduler as, String resName) {
		// sort web apps by decreasing profit
		int[] appIdxSorted = HeuristicsHelper.sortWebAppsByMaxProfit(as);
		// sort servers by increasing remaining
		int[] serverIdxSorted = HeuristicsHelper.sortServersByRemaining(as, resName);
		List<AbstractStrategy<?>> strats = new ArrayList<>();
		for (int appIdx : appIdxSorted) {
			List<WebSubClass> modes = as.webModes.get(appIdx);
			for (int itv = 0; itv < modes.size(); itv++) {
				strats.add(HeuristicsHelper.assignWebProfitThenServer(as, modes.get(itv).profit, as.position(itv, appIdx),
						serverIdxSorted));
			}
		}
		return Search.sequencer(strats.toArray(new AbstractStrategy[] {}));
	}

	/**
	 * set the we task to their highest profit, placing it on the server with the least
	 */
	public static final Heuristic WEB_HIGH_PROFIT_REMAIN_RAM = sc -> new StrategiesSequencer(
			HeuristicsHelper.assignWebProfitThenServer(sc, "ram"), Search.defaultSearch(sc));

	/**
	 * put hpc apps on schedule, with best interest first. Interest is profit/(product of resource weight * power)
	 *
	 * @param as
	 *          the scheduler
	 * @return a new strategy
	 */
	@SuppressWarnings("unchecked")
	public static AbstractStrategy<?> scheduleHPCBestInterest(Scheduler as) {
		List<ToIntFunction<String>> resources = new ArrayList<>();
		resources.add(n -> as.getSource().getHPC(n).power);
		as.getSource().resources().map(Entry::getValue).forEachOrdered(resources::add);
		ToIntFunction<String>[] functions = resources.toArray(new ToIntFunction[] {});
		ToIntFunction<String> multipliedRes = n -> {
			int ret = 1;
			for (ToIntFunction<String> tif : functions) {
				ret *= tif.applyAsInt(n);
			}
			return ret;
		};
		int[] sortedHPCApps = HeuristicsHelper.sortHpcAppsByInterest(as, multipliedRes);
		return Search.inputOrderUBSearch(IntStream.of(sortedHPCApps).mapToObj(idx -> {
			List<HPCSubTask> l = as.getHPCTasks(as.app(idx));
			return l.get(l.size() - 1).getOnSchedule();
		}).toArray(BoolVar[]::new));
	}
	public static final Heuristic HPC_INTEREST_FIRST = sc -> new StrategiesSequencer(
			HeuristicsHelper.scheduleHPCBestInterest(sc), Search.defaultSearch(sc));

}
