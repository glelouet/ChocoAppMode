package fr.lelouet.choco.limitPower;

import static fr.lelouet.choco.limitpower.AppScheduler.solv;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import fr.lelouet.choco.limitpower.Model;
import fr.lelouet.choco.limitpower.Result;

/**
 * test the limit of power on some intervals
 * 
 * @author Guillaume Le Louët
 *
 */
public class TestLimit {

	Result r;
	Model m;

	@BeforeMethod
	public void cleanup() {
		m = new Model();
	}

	@Test
	public void testLimit() {
		m.setLimit(0, 2);
		m.setLimit(1, 2);
		m.addHPC("a", 0, 1, 2, 1, 1000);
		m.maxPower = 2;
		m.nbIntervals = 2;

		// we limit the power by two on both intervals : the hpc can't be scheduled
		r = solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 0, "" + r);

		// we limit the power by 1 on first intervals : the hpc is scheduled on
		// second interval
		m.setLimit(0, 1);
		m.setLimit(1, 0);
		r = solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 1, "" + r);
		Assert.assertEquals(r.hpcStarts.get("a"), Arrays.asList(new Integer(1)));

		// we limit the power by 1 on last intervals : the hpc is scheduled on first
		// interval
		m.setLimit(0, 0);
		m.setLimit(1, 1);
		r = solv(m);
		Assert.assertNotNull(r);
		Assert.assertEquals(r.profit, 1, "" + r);
		Assert.assertEquals(r.hpcStarts.get("a"), Arrays.asList(new Integer(0)));
	}


}