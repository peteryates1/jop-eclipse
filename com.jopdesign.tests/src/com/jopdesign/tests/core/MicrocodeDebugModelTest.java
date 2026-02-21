package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.microcode.MicrocodeParser;
import com.jopdesign.core.sim.microcode.MicrocodeParser.MicrocodeParseException;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;
import com.jopdesign.core.sim.microcode.MicrocodeSimulator;
import com.jopdesign.microcode.debug.MicrocodeDebugTarget;
import com.jopdesign.microcode.debug.MicrocodeStackFrame;
import com.jopdesign.microcode.debug.MicrocodeThread;

/**
 * Tests for the microcode debug model (target, thread, frame, variables).
 */
public class MicrocodeDebugModelTest {

	private MicrocodeParser parser;
	private MicrocodeSimulator simulator;
	private ILaunch launch;

	@Before
	public void setUp() throws MicrocodeParseException {
		parser = new MicrocodeParser();
		simulator = new MicrocodeSimulator();

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
		simulator.load(prog);

		launch = new Launch(null, "debug", null);
	}

	@Test
	public void testDebugTargetCreation() {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		assertEquals("Test", target.getName());
		assertEquals(MicrocodeDebugTarget.MODEL_ID, target.getModelIdentifier());
		assertSame(launch, target.getLaunch());
		assertSame(target, target.getDebugTarget());
	}

	@Test
	public void testDebugTargetHasThread() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		assertTrue(target.hasThreads());
		IThread[] threads = target.getThreads();
		assertEquals(1, threads.length);
		assertEquals("Microcode Execution", threads[0].getName());
	}

	@Test
	public void testThreadSuspendedAtStart() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		IThread thread = target.getThreads()[0];
		assertTrue(thread.isSuspended());
		assertTrue(thread.canResume());
		assertTrue(thread.canStepOver());
		assertFalse(thread.isTerminated());
	}

	@Test
	public void testStackFrameOnSuspend() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		IThread thread = target.getThreads()[0];
		assertTrue(thread.hasStackFrames());

		IStackFrame[] frames = thread.getStackFrames();
		assertEquals(1, frames.length);
		assertTrue(frames[0] instanceof MicrocodeStackFrame);
	}

	@Test
	public void testStackFrameVariables() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		IThread thread = target.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		assertNotNull(frame);

		assertTrue(frame.hasVariables());
		IVariable[] vars = frame.getVariables();
		assertTrue(vars.length >= 7); // At least the 7 registers

		// Check register names
		assertEquals("A (TOS)", vars[0].getName());
		assertEquals("B (NOS)", vars[1].getName());
		assertEquals("pc", vars[2].getName());
		assertEquals("sp", vars[3].getName());
		assertEquals("vp", vars[4].getName());
		assertEquals("ar", vars[5].getName());
		assertEquals("jpc", vars[6].getName());
	}

	@Test
	public void testVariableValueFormat() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		IThread thread = target.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		IVariable[] vars = frame.getVariables();

		// A (TOS) should be 0 initially
		String valueStr = vars[0].getValue().getValueString();
		assertTrue(valueStr.contains("0"));
		assertTrue(valueStr.contains("0x"));
	}

	@Test
	public void testStepUpdatesFrame() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");

		// Step: execute ldi val1
		target.stepOver();

		IThread thread = target.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		assertNotNull(frame);

		IVariable[] vars = frame.getVariables();
		// A should now be 10
		String aValue = vars[0].getValue().getValueString();
		assertTrue("A should be 10 after ldi val1, got: " + aValue, aValue.startsWith("10 "));
	}

	@Test
	public void testFrameLineNumber() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		IThread thread = target.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();

		// At start, should be line 4 (ldi val1, after the constant/label declarations)
		int line = frame.getLineNumber();
		assertEquals(4, line);
	}

	@Test
	public void testFrameNameIncludesLine() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		IThread thread = target.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();

		String name = frame.getName();
		assertTrue("Frame name should include mnemonic, got: " + name, name.contains("ldi"));
		assertTrue("Frame name should include line number, got: " + name, name.contains("line"));
	}

	@Test
	public void testTermination() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		assertFalse(target.isTerminated());
		assertTrue(target.canTerminate());

		target.terminate();
		assertTrue(target.isTerminated());
		assertFalse(target.canTerminate());
	}

	@Test
	public void testNoStackFrameWhenTerminated() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		target.terminate();

		IThread thread = target.getThreads()[0];
		assertFalse(thread.hasStackFrames());
		assertNull(thread.getTopStackFrame());
	}

	@Test
	public void testModelIdentifierConsistency() throws Exception {
		MicrocodeDebugTarget target = new MicrocodeDebugTarget(launch, simulator, "Test");
		IThread thread = target.getThreads()[0];
		IStackFrame frame = thread.getTopStackFrame();
		IVariable[] vars = frame.getVariables();

		assertEquals(MicrocodeDebugTarget.MODEL_ID, target.getModelIdentifier());
		assertEquals(MicrocodeDebugTarget.MODEL_ID, thread.getModelIdentifier());
		assertEquals(MicrocodeDebugTarget.MODEL_ID, frame.getModelIdentifier());
		assertEquals(MicrocodeDebugTarget.MODEL_ID, vars[0].getModelIdentifier());
		assertEquals(MicrocodeDebugTarget.MODEL_ID, vars[0].getValue().getModelIdentifier());
	}
}
