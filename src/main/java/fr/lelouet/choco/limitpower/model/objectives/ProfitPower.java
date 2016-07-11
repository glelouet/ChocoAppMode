/**
 *
 */
package fr.lelouet.choco.limitpower.model.objectives;

import org.chocosolver.solver.variables.IntVar;

import fr.lelouet.choco.limitpower.AppScheduler;
import fr.lelouet.choco.limitpower.AppScheduler.HPCSubTask;
import fr.lelouet.choco.limitpower.model.Heuristic;
import fr.lelouet.choco.limitpower.model.Objective;

/**
 * @author Guillaume Le Louët [guillaume.lelouet@gmail.com] 2016
 *
 */
public class ProfitPower implements Objective {

	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProfitPower.class);

	public static final ProfitPower INSTANCE = new ProfitPower();

	@Override
	public IntVar getObjective(AppScheduler s) {
		return s.makeSubtaskSum("profit_power", HPCSubTask::getPower,
				s.intScaleView(s.getProfit(), s.getMaxPower() * s.getSource().nbIntervals));
	}

	final Heuristic[] strategies;

	/**
	 *
	 */
	public ProfitPower() {
		this((Heuristic[]) null);
	}

	public ProfitPower(Heuristic... strategies) {
		this.strategies = strategies;
	}

	@Override
	public Heuristic[] getStrategies() {
		return strategies;
	}

}
