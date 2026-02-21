package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.DummyJopTarget;
import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.SimulatorJopTarget;
import com.jopdesign.core.sim.microcode.MicrocodeParser;
import com.jopdesign.core.sim.microcode.MicrocodeParser.MicrocodeParseException;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;
import com.jopdesign.microcode.debug.JopDebugTarget;
import com.jopdesign.microcode.debug.JopStackFrame;
import com.jopdesign.microcode.debug.JopThread;

/**
 * Tests for the unified JOP debug model (JopDebugTarget, JopThread, JopStackFrame)
 * with both SimulatorJopTarget and DummyJopTarget.
 */
public class JopDebugModelTest {

	private ILaunch launch;
	private SimulatorJopTarget simTarget;
	private DummyJopTarget dummyTarget;

	@Before
	public void setUp() throws MicrocodeParseException, JopTargetException {
		launch = new Launch(null, "debug", null);

		// Set up simulator target
		MicrocodeParser parser = new MicrocodeParser();
		String source = """
				val1 = 10
				val2 = 20
				start:
				ldi val1
				ldi val2
				add
				nop
				""";
		MicrocodeProgram prog = parser.parse(source);
		simTarget = new SimulatorJopTarget(prog, 1024, 1024, 64);
		simTarget.connect();

		// Set up dummy target
		dummyTarget = new DummyJopTarget();
		dummyTarget.connect();
	}

	// --- Simulator-backed debug target tests ---

	@Test
	public void testSimDebugTargetCreation() {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		assertEquals("SimTest", dt.getName());
		assertEquals(JopDebugTarget.MODEL_ID, dt.getModelIdentifier());
		assertSame(launch, dt.getLaunch());
		assertSame(dt, dt.getDebugTarget());
	}

	@Test
	public void testSimDebugTargetHasThread() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		assertTrue(dt.hasThreads());
		IThread[] threads = dt.getThreads();
		assertEquals(1, threads.length);
		assertEquals("JOP Execution", threads[0].getName());
	}

	@Test
	public void testSimThreadSuspendedAtStart() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		IThread thread = dt.getThreads()[0];
		assertTrue(thread.isSuspended());
		assertTrue(thread.canResume());
		assertTrue(thread.canStepOver());
		assertTrue(thread.canStepInto());
		assertFalse(thread.isTerminated());
	}

	@Test
	public void testSimStackFrame() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		IThread thread = dt.getThreads()[0];
		assertTrue(thread.hasStackFrames());
		IStackFrame[] frames = thread.getStackFrames();
		assertEquals(1, frames.length);
		assertTrue(frames[0] instanceof JopStackFrame);
	}

	@Test
	public void testSimStackFrameVariables() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		IThread thread = dt.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		assertNotNull(frame);

		assertTrue(frame.hasVariables());
		IVariable[] vars = frame.getVariables();
		// Should have at least 15 registers + stack entries
		assertTrue(vars.length >= 15);

		assertEquals("A (TOS)", vars[0].getName());
		assertEquals("B (NOS)", vars[1].getName());
		assertEquals("pc", vars[2].getName());
		assertEquals("sp", vars[3].getName());
		assertEquals("vp", vars[4].getName());
		assertEquals("ar", vars[5].getName());
		assertEquals("jpc", vars[6].getName());
		assertEquals("mulA", vars[7].getName());
		assertEquals("mulB", vars[8].getName());
		assertEquals("mulResultLo", vars[9].getName());
		assertEquals("mulResultHi", vars[10].getName());
		assertEquals("memReadAddr", vars[11].getName());
		assertEquals("memWriteAddr", vars[12].getName());
		assertEquals("memWriteData", vars[13].getName());
		assertEquals("memReadData", vars[14].getName());
	}

	@Test
	public void testSimStepOverUpdatesState() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		dt.stepOver(); // ldi val1

		IThread thread = dt.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		assertNotNull(frame);
		IVariable[] vars = frame.getVariables();
		String aValue = vars[0].getValue().getValueString();
		assertTrue("A should be 10, got: " + aValue, aValue.startsWith("10 "));
	}

	@Test
	public void testSimStepInto() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		// stepInto does bytecode step (loops until jpc changes or terminates)
		dt.stepInto();
		// Should reach a valid state
		assertFalse(dt.isTerminated() && dt.isSuspended()); // can't be both
	}

	@Test
	public void testSimTermination() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		assertFalse(dt.isTerminated());
		dt.terminate();
		assertTrue(dt.isTerminated());
	}

	@Test
	public void testSimFrameLineNumber() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		IThread thread = dt.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		assertEquals(4, frame.getLineNumber()); // ldi val1
	}

	@Test
	public void testSimFrameName() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		IThread thread = dt.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		String name = frame.getName();
		assertTrue("Name should include mnemonic, got: " + name, name.contains("ldi"));
		assertTrue("Name should include line, got: " + name, name.contains("line"));
	}

	@Test
	public void testSimValueFormat() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "SimTest");
		IThread thread = dt.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		IVariable[] vars = frame.getVariables();
		String valueStr = vars[0].getValue().getValueString();
		assertTrue(valueStr.contains("0x"));
	}

	// --- Dummy-backed debug target tests ---

	@Test
	public void testDummyDebugTargetCreation() {
		JopDebugTarget dt = new JopDebugTarget(launch, dummyTarget, "DummyTest");
		assertEquals("DummyTest", dt.getName());
		assertEquals(JopDebugTarget.MODEL_ID, dt.getModelIdentifier());
	}

	@Test
	public void testDummyDebugTargetSuspendedAtStart() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, dummyTarget, "DummyTest");
		assertTrue(dt.isSuspended());
		assertTrue(dt.canResume());
	}

	@Test
	public void testDummyStackFrameVariables() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, dummyTarget, "DummyTest");
		IThread thread = dt.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		assertNotNull(frame);
		IVariable[] vars = frame.getVariables();
		assertTrue(vars.length >= 15);

		// Check initial canned A=42
		String aValue = vars[0].getValue().getValueString();
		assertTrue("A should be 42, got: " + aValue, aValue.startsWith("42 "));
	}

	@Test
	public void testDummyStepOver() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, dummyTarget, "DummyTest");
		dt.stepOver();
		assertTrue(dt.isSuspended());
	}

	@Test
	public void testDummyTermination() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, dummyTarget, "DummyTest");
		dt.terminate();
		assertTrue(dt.isTerminated());
	}

	@Test
	public void testDummyNoStackFrameWhenTerminated() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, dummyTarget, "DummyTest");
		dt.terminate();
		IThread thread = dt.getThreads()[0];
		assertFalse(thread.hasStackFrames());
		assertNull(thread.getTopStackFrame());
	}

	@Test
	public void testModelIdentifierConsistency() throws Exception {
		JopDebugTarget dt = new JopDebugTarget(launch, simTarget, "Test");
		IThread thread = dt.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		IVariable[] vars = frame.getVariables();

		assertEquals(JopDebugTarget.MODEL_ID, dt.getModelIdentifier());
		assertEquals(JopDebugTarget.MODEL_ID, thread.getModelIdentifier());
		assertEquals(JopDebugTarget.MODEL_ID, frame.getModelIdentifier());
		assertEquals(JopDebugTarget.MODEL_ID, vars[0].getModelIdentifier());
		assertEquals(JopDebugTarget.MODEL_ID, vars[0].getValue().getModelIdentifier());
	}
}
