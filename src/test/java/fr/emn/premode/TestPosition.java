package fr.emn.premode;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import fr.emn.premode.Scheduler;
import fr.emn.premode.Planning;
import fr.emn.premode.center.SchedulingProblem;

public class TestPosition {

	Planning r;
	SchedulingProblem m;

	@BeforeMethod
	public void cleanup() {
		m = new SchedulingProblem();
		m.server("s1").maxPower = 1000;
	}

	@Test
	// two intervals, one hpc app. the app should simply be put.
	public void testSimpleHPCApp() {
		m.nbIntervals = 2;
		m.addHPC("hpc", 0, 2, 1, 1, -1);
		r = Scheduler.solv(m);
		Assert.assertEquals(r.profit, 1, "" + r);
		Assert.assertEquals(r.appHosters.get("hpc"), Arrays.asList("s1", "s1"), "" + r);
	}

	@Test
	// 3 intervals, one server with 2 power cap, one with 1 power cap.<br />
	// the HPC consumes 1 power.
	// the web has two modes, gain=power*2-1, power=0 or 1
	// one reduction by 1 power at the 2d interval (itv 1)
	public void testScheduling2apps() {
		m.nbIntervals = 3;
		m.setPower(1, 2);
		m.addHPC("hpc", 0, 2, 1, 4, -1);
		m.addWeb("web", 1, 1);
		m.addWeb("web", 2, 3);
		m.server("s0").maxPower = 1;
		m.server("s1").maxPower = 2;
		Scheduler s = new Scheduler();
		r = s.solve(m);
		Assert.assertEquals(r.appHosters.get("web"), Arrays.asList("s1", "s1", "s1"), "" + r);
		Assert.assertEquals(r.appHosters.get("hpc"), Arrays.asList("s0", null, "s0"), "" + r);
	}

	/**
	 * <p>
	 * this time 3 servers. s1 has 1 power, s2 2, s3 has 3 power. total power is
	 * 6 , but limited to 5 at interval 1 (the 2nd)
	 * </p>
	 * <p>
	 * 3 web apps, each with 3 modes. each consumes 1 pwr and gets one benefits
	 * at mode 1. On mode 2 each app consumes 2 power and 3, 4, 5 profit; On
	 * mode 3 each app consumes 3 power and 5,6,7 profit
	 * </p>
	 */
	@Test
	public void moreComplexWebProblem() {

		// 2 intervals, 2 servers
		m.nbIntervals = 2;
		m.server("s0").maxPower = 1;
		m.server("s1").maxPower = 2;
		m.setPower(1, 2);

		m.addWeb("w0", 1, 1);
		m.addWeb("w0", 2, 3);
		m.addWeb("w0", 3, 5);

		m.addWeb("w1", 1, 1);
		m.addWeb("w1", 2, 4);
		m.addWeb("w1", 3, 6);

		m.setMigrateCost(n -> 1);

		r = new Scheduler().solve(m);
		Assert.assertEquals(r.profit, 7);
		Assert.assertEquals(r.appHosters.get("w1"), Arrays.asList("s1", "s1"), "" + r);
		Assert.assertEquals(r.appHosters.get("w0"), Arrays.asList("s0", "s0"), "" + r);

		// then with 3 intervals and 3 servers
		m.nbIntervals = 3;
		m.server("s2").maxPower = 3;
		m.addWeb("w2", 1, 1);
		m.addWeb("w2", 2, 5);
		m.addWeb("w2", 3, 8);
		m.setPower(1, 5);
		r = new Scheduler().solve(m);
		Assert.assertEquals(r.profit, 36);
		Assert.assertEquals(r.appHosters.get("w2"), Arrays.asList("s2", "s2", "s2"), "" + r);
		Assert.assertEquals(r.appHosters.get("w1"), Arrays.asList("s1", "s1", "s1"), "" + r);
		Assert.assertEquals(r.appHosters.get("w0"), Arrays.asList("s0", "s0", "s0"), "" + r);
	}

}
