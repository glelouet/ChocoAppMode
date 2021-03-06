package fr.emn.premode;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import fr.emn.premode.Scheduler;
import fr.emn.premode.Planning;
import fr.emn.premode.center.SchedulingProblem;

/**
 * test the limit of power on some intervals
 *
 * @author Guillaume Le Louët
 *
 */
public class TestLimit {

	Planning r;
	SchedulingProblem m;

	@BeforeMethod
	public void cleanup() {
		m = new SchedulingProblem();
		m.server("server").maxPower = 1000;
	}

	@Test
	public void testLimit() {
		m.addHPC("a", 0, 1, 2, 1, -1);
		m.nbIntervals = 2;

		// we limit the power by two on both intervals : the hpc can't be scheduled
		m.setPower(0, 0);
		m.setPower(1, 0);
		r = Scheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 0, "" + r);

		// we limit the power by 1 on first intervals : the hpc is scheduled on
		// second interval
		m.setPower(0, 1);
		m.setPower(1, 2);
		r = Scheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 1, "" + r);
		Assert.assertEquals(r.hpcStarts.get("a"), Arrays.asList(new Integer(1)));

		// we limit the power by 1 on last intervals : the hpc is scheduled on first
		// interval
		m.setPower(0, 2);
		m.setPower(1, 1);
		r = Scheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 1, "" + r);
		Assert.assertEquals(r.hpcStarts.get("a"), Arrays.asList(new Integer(0)));
	}


}
