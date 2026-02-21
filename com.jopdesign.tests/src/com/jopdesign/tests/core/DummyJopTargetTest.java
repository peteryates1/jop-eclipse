package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.DummyJopTarget;
import com.jopdesign.core.sim.IJopTargetListener;
import com.jopdesign.core.sim.JopMemoryData;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopStackData;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.JopTargetState;

/**
 * Tests for {@link DummyJopTarget}.
 */
public class DummyJopTargetTest {

	private DummyJopTarget target;

	@Before
	public void setUp() throws JopTargetException {
		target = new DummyJopTarget();
	}

	@Test
	public void testNameAndTypeId() {
		assertEquals("JOP Dummy Target", target.getName());
		assertEquals("dummy", target.getTargetTypeId());
	}

	@Test
	public void testInitialState() {
		assertEquals(JopTargetState.NOT_STARTED, target.getState());
	}

	@Test
	public void testConnect() throws JopTargetException {
		target.connect();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
	}

	@Test
	public void testStepMicro() throws JopTargetException {
		target.connect();
		target.stepMicro();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
	}

	@Test
	public void testStepMicroIncrementsPC() throws JopTargetException {
		target.connect();
		JopRegisters before = target.readRegisters();
		int pcBefore = before.pc();
		target.stepMicro();
		JopRegisters after = target.readRegisters();
		assertEquals(pcBefore + 1, after.pc());
	}

	@Test
	public void testStepBytecode() throws JopTargetException {
		target.connect();
		target.stepBytecode();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
		JopRegisters regs = target.readRegisters();
		assertEquals(1, regs.jpc()); // jpc incremented
	}

	@Test
	public void testResumeSuspendsImmediately() throws JopTargetException {
		target.connect();
		target.resume();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
	}

	@Test
	public void testTerminate() throws JopTargetException {
		target.connect();
		target.terminate();
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	@Test
	public void testDisconnect() throws JopTargetException {
		target.connect();
		target.disconnect();
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	@Test
	public void testReadRegistersReturnsValidData() throws JopTargetException {
		target.connect();
		JopRegisters regs = target.readRegisters();
		assertNotNull(regs);
		assertEquals(42, regs.a()); // initial canned value
		assertEquals(7, regs.b());
	}

	@Test
	public void testReadStackReturnsValidData() throws JopTargetException {
		target.connect();
		JopStackData stack = target.readStack();
		assertNotNull(stack);
		assertTrue(stack.values().length > 0);
		assertEquals(4, stack.sp());
	}

	@Test
	public void testReadMemory() throws JopTargetException {
		target.connect();
		JopMemoryData mem = target.readMemory(10, 5);
		assertNotNull(mem);
		assertEquals(10, mem.startAddress());
		assertEquals(5, mem.values().length);
	}

	@Test
	public void testWriteRegister() throws JopTargetException {
		target.connect();
		target.writeRegister("a", 999);
		JopRegisters regs = target.readRegisters();
		assertEquals(999, regs.a());
	}

	@Test(expected = JopTargetException.class)
	public void testWriteUnknownRegister() throws JopTargetException {
		target.connect();
		target.writeRegister("invalid", 0);
	}

	@Test
	public void testCurrentSourceLine() throws JopTargetException {
		target.connect();
		int line = target.getCurrentSourceLine();
		assertTrue(line > 0);
	}

	@Test
	public void testCurrentInstructionName() throws JopTargetException {
		target.connect();
		String name = target.getCurrentInstructionName();
		assertNotNull(name);
		assertFalse(name.isEmpty());
	}

	@Test
	public void testStateChangeListener() throws JopTargetException {
		List<JopTargetState> states = new ArrayList<>();
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState) {
				states.add(newState);
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.connect();
		assertTrue(states.contains(JopTargetState.SUSPENDED));
	}

	@Test
	public void testResumeFiresRunningThenSuspended() throws JopTargetException {
		List<JopTargetState> states = new ArrayList<>();
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState) {
				states.add(newState);
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.connect();
		states.clear();
		target.resume();

		assertEquals(2, states.size());
		assertEquals(JopTargetState.RUNNING, states.get(0));
		assertEquals(JopTargetState.SUSPENDED, states.get(1));
	}

	@Test
	public void testBreakpointsNoOp() throws JopTargetException {
		target.connect();
		// Should not throw
		target.addBreakpoint(5);
		target.removeBreakpoint(5);
	}

	@Test
	public void testProvideInputNoOp() throws JopTargetException {
		target.connect();
		target.provideInput("test");
	}

	@Test
	public void testWriteMemoryNoOp() throws JopTargetException {
		target.connect();
		target.writeMemory(0, 42);
	}

	@Test(expected = JopTargetException.class)
	public void testStepMicroAfterTerminate() throws JopTargetException {
		target.connect();
		target.terminate();
		target.stepMicro();
	}

	@Test(expected = JopTargetException.class)
	public void testResumeAfterTerminate() throws JopTargetException {
		target.connect();
		target.terminate();
		target.resume();
	}

	@Test(expected = JopTargetException.class)
	public void testSuspendAfterTerminate() throws JopTargetException {
		target.connect();
		target.terminate();
		target.suspend();
	}
}
