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
 */
public class ProfitOnSchedule implements Objective {

	@SuppressWarnings("unused")
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProfitOnSchedule.class);

	public static final ProfitOnSchedule INSTANCE = new ProfitOnSchedule();

	@Override
	public IntVar getObjective(AppScheduler s) {
		return s.makeSubtaskSum("profit_onschedule", HPCSubTask::getOnSchedule,
				s.intScaleView(s.getProfit(), s.getSource().hpcNames().mapToInt(n -> s.getSource().getHPC(n).duration).sum()));
	}

	final Heuristic[] strategies;

	/**
	 *
	 */
	public ProfitOnSchedule() {
		this((Heuristic[]) null);
	}

	public ProfitOnSchedule(Heuristic... strategies) {
		this.strategies = strategies;
	}

	@Override
	public Heuristic[] getStrategies() {
		return strategies;
	}

}
