package fr.lelouet.choco.limitPower;

import static fr.lelouet.choco.limitpower.AppScheduler.solv;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import fr.lelouet.choco.limitpower.SchedulingModel;
import fr.lelouet.choco.limitpower.SchedulingModel.Objective;
import fr.lelouet.choco.limitpower.SchedulingResult;

/**
 * test the solver with objective being profit+power use
 *
 * @author Guillaume Le Louët
 *
 */
public class TestObjectivePower {

	SchedulingResult r;
	SchedulingModel m;

	@BeforeMethod
	public void cleanup() {
		m = new SchedulingModel();
		m.server("server").maxPower = 1000;
		m.objective = Objective.PROFIT_POWER;
	}

	/**
	 * one app with 1 power and 1 benefit, 2 intervals ; problem with 1 power, but
	 * only one intervals. The best solution is to schedule the app on the one
	 * interval
	 */
	@Test
	public void testOneAppOneInterval() {
		m.setPower(1);
		m.nbIntervals = 1;
		m.addHPC("a", 0, 2, 1, 1, 10);
		r = solv(m);
		Assert.assertEquals(r.hpcStarts.get("a"), Arrays.asList(new Integer[] { 0 }), "result is " + r);
	}

	/**
	 * one app with 1 power and 1 benefit, 2 intervals ; problem with 1 power, but
	 * only one intervals. The best solution is to schedule the app on the one
	 * interval
	 */
	@Test
	public void testOneAppTwoInterval() {
		m.setPower(1);
		m.nbIntervals = 2;
		m.addHPC("a", 0, 3, 1, 1, 10);
		r = solv(m);
		Assert.assertEquals(r.hpcStarts.get("a"), Arrays.asList(new Integer[] { 0, 1 }), "result is " + r);
	}

	/**
	 * two apps with 2 and 3 power and benefit=power, 2 intervals ; problem with 3
	 * power, 3 intervals. The best solution is to schedule the 3-power first then
	 * the 2-power on one interval
	 */
	@Test
	public void testTwoApps() {
		m.setPower(3);
		m.nbIntervals = 3;
		m.addHPC("a", 0, 2, 2, 2, 10);
		m.addHPC("b", 0, 2, 3, 3, 10);
		r = solv(m);
		Assert.assertEquals(r.hpcStarts.get("a").size(), 1, "result is " + r);
		Assert.assertEquals(r.hpcStarts.get("b").size(), 2, "result is " + r);
	}

}
