package fr.lelouet.choco.limitPower;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import fr.lelouet.choco.limitpower.AppScheduler;
import fr.lelouet.choco.limitpower.SchedulingResult;
import fr.lelouet.choco.limitpower.model.SchedulingProblem;
import fr.lelouet.choco.limitpower.model.SchedulingProblem.Server;

public class TestSimpleWebApplication {

	AppScheduler s;
	SchedulingResult r;
	SchedulingProblem m;
	Server server;

	@BeforeMethod
	public void cleanup() {
		m = new SchedulingProblem();
		server = m.server("server");
	}

	@Test
	public void testOnePassingWeb() {
		m.nameWeb("a").add(1, 1).add(5, 3);

		server.maxPower = 2;
		m.nbIntervals = 1;
		r = AppScheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.webModes.get("a").get(0).power, 1);
		Assert.assertEquals(r.profit, 1);

		// now we allow 5 power use, so we can have a profit of 3
		server.maxPower = 5;
		m.nbIntervals = 1;
		r = AppScheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.webModes.get("a").get(0).power, 5);
		Assert.assertEquals(r.profit, 3);
	}

	/**
	 * two web apps, with increasing power/benefit both. The first web app (a)
	 * consumes one less power than the second (b), for the same profit.
	 */
	@Test
	public void testConcurrentWebApps() {
		m.nameWeb("a").add(1, 1).add(5, 2).add(17, 6);
		m.nameWeb("b").add(2, 1).add(6, 2).add(18, 6);

		server.maxPower = 3;
		m.nbIntervals = 1;
		r = AppScheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 2);

		server.maxPower = 7;
		r = AppScheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 3);

		server.maxPower = 11;
		r = AppScheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 4);

		server.maxPower = 40;
		r = AppScheduler.solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 12);
	}

}
