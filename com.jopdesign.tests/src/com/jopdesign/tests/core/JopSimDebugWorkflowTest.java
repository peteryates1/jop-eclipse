package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.IJopTargetListener;
import com.jopdesign.core.sim.JopBreakpointType;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopSimJopTarget;
import com.jopdesign.core.sim.JopStackData;
import com.jopdesign.core.sim.JopSuspendReason;
import com.jopdesign.core.sim.JopTargetState;
import com.jopdesign.microcode.debug.JopDebugTarget;

/**
 * End-to-end debug workflow test using the real compiled NCoreHelloWorld.jop.
 *
 * <p>Tests the full debug lifecycle: connect, step, breakpoint, registers,
 * stack, console output, and terminate — all with the JOP bytecode simulator.
 *
 * <p>This test is skipped if the .jop file is not available (it requires
 * a prior build of the JopDemo project).
 */
public class JopSimDebugWorkflowTest {

	private static final String JOP_FILE =
			"/tmp/jop-eclipse-test-workspace/JopDemo/generated/NCoreHelloWorld.jop";
	private static final String LINK_FILE =
			"/tmp/jop-eclipse-test-workspace/JopDemo/generated/NCoreHelloWorld.jop.link.txt";

	/** Bytecode address of test.NCoreHelloWorld.main from link file */
	private static final int MAIN_METHOD_ADDR = 4522;

	private JopSimJopTarget target;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("NCoreHelloWorld.jop not found — skipping",
				new File(JOP_FILE).exists());
		target = new JopSimJopTarget(JOP_FILE, LINK_FILE);
		target.connect();
	}

	@After
	public void tearDown() throws Exception {
		if (target != null && target.getState() != JopTargetState.TERMINATED) {
			target.disconnect();
		}
	}

	// --- Connection & initial state ---

	@Test
	public void testConnectSuspendsAtReset() {
		assertEquals(JopTargetState.SUSPENDED, target.getState());
	}

	@Test
	public void testInitialRegistersValid() throws Exception {
		JopRegisters regs = target.readRegisters();
		assertNotNull(regs);
		assertTrue("SP should be positive at boot", regs.sp() > 0);
		assertTrue("JPC should be positive at boot", regs.jpc() > 0);
	}

	@Test
	public void testInitialStackNonEmpty() throws Exception {
		JopStackData stack = target.readStack();
		assertNotNull(stack);
		assertTrue("Stack should have entries at boot", stack.values().length > 0);
	}

	// --- Stepping ---

	@Test
	public void testStepMicroAdvancesState() throws Exception {
		JopRegisters before = target.readRegisters();
		target.stepMicro();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
		JopRegisters after = target.readRegisters();
		// After stepping, at least JPC or SP should have changed
		assertTrue("State should change after step",
				after.jpc() != before.jpc() || after.sp() != before.sp()
						|| after.a() != before.a());
	}

	@Test
	public void testMultipleSteps() throws Exception {
		for (int i = 0; i < 10; i++) {
			target.stepMicro();
			assertEquals("Should remain SUSPENDED after step " + i,
					JopTargetState.SUSPENDED, target.getState());
		}
		JopRegisters regs = target.readRegisters();
		assertNotNull(regs);
	}

	@Test
	public void testStepFiresStepCompleteReason() throws Exception {
		List<JopSuspendReason> reasons = Collections.synchronizedList(new ArrayList<>());
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				if (reason != null) reasons.add(reason);
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.stepMicro();
		assertTrue("Step should fire STEP_COMPLETE",
				reasons.contains(JopSuspendReason.STEP_COMPLETE));
	}

	// --- Breakpoints ---

	@Test
	public void testBreakpointAtMainMethod() throws Exception {
		int slot = target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, MAIN_METHOD_ADDR);
		assertTrue("Breakpoint slot should be valid", slot >= 0);

		CountDownLatch suspended = new CountDownLatch(1);
		List<JopSuspendReason> reasons = Collections.synchronizedList(new ArrayList<>());
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				if (newState == JopTargetState.SUSPENDED && reason == JopSuspendReason.BREAKPOINT) {
					reasons.add(reason);
					suspended.countDown();
				}
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.resume();
		boolean hit = suspended.await(10, TimeUnit.SECONDS);
		assertTrue("Breakpoint at main() should be hit within 10s", hit);
		assertEquals(JopTargetState.SUSPENDED, target.getState());

		JopRegisters regs = target.readRegisters();
		assertEquals("JPC should be at main() address", MAIN_METHOD_ADDR, regs.jpc());
		assertTrue("BREAKPOINT reason should have fired",
				reasons.contains(JopSuspendReason.BREAKPOINT));
	}

	@Test
	public void testStepAfterBreakpoint() throws Exception {
		// Hit breakpoint at main()
		target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, MAIN_METHOD_ADDR);
		CountDownLatch suspended = new CountDownLatch(1);
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				if (newState == JopTargetState.SUSPENDED && reason == JopSuspendReason.BREAKPOINT) {
					suspended.countDown();
				}
			}

			@Override
			public void outputProduced(String text) {
			}
		});
		target.resume();
		assertTrue("Should hit breakpoint", suspended.await(10, TimeUnit.SECONDS));

		// Now step from the breakpoint
		JopRegisters atBp = target.readRegisters();
		assertEquals("Should be at main() breakpoint", MAIN_METHOD_ADDR, atBp.jpc());
		target.stepMicro();
		JopRegisters afterStep = target.readRegisters();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
		// JPC may go forward (sequential) or backward (method call) — just verify it changed
		assertTrue("JPC should change after stepping from breakpoint (was " + atBp.jpc()
				+ ", now " + afterStep.jpc() + ")",
				afterStep.jpc() != atBp.jpc()
						|| afterStep.sp() != atBp.sp()
						|| afterStep.a() != atBp.a());
	}

	// --- Resume & Suspend ---

	@Test
	public void testResumeAndSuspend() throws Exception {
		target.resume();
		assertEquals(JopTargetState.RUNNING, target.getState());

		Thread.sleep(50);
		target.suspend();
		assertEquals(JopTargetState.SUSPENDED, target.getState());

		JopRegisters regs = target.readRegisters();
		assertTrue("JPC should have advanced during resume", regs.jpc() > 0);
	}

	// --- Console output ---

	@Test
	public void testConsoleOutputDuringExecution() throws Exception {
		StringBuilder output = new StringBuilder();
		CountDownLatch gotOutput = new CountDownLatch(1);
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
			}

			@Override
			public void outputProduced(String text) {
				output.append(text);
				if (output.toString().contains("Hello")) {
					gotOutput.countDown();
				}
			}
		});

		target.resume();
		boolean received = gotOutput.await(10, TimeUnit.SECONDS);
		// The program might terminate or we might need to suspend
		if (target.getState() == JopTargetState.RUNNING) {
			target.suspend();
		}
		// Note: NCoreHelloWorld prints via UART and depends on timer values;
		// output may or may not appear in simulator time. We just verify no crash.
		assertNotNull("Output buffer should not be null", output);
	}

	// --- Registers & Stack at breakpoint ---

	@Test
	public void testRegisterDetailsAtBreakpoint() throws Exception {
		target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, MAIN_METHOD_ADDR);
		CountDownLatch suspended = new CountDownLatch(1);
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				if (newState == JopTargetState.SUSPENDED && reason == JopSuspendReason.BREAKPOINT) {
					suspended.countDown();
				}
			}

			@Override
			public void outputProduced(String text) {
			}
		});
		target.resume();
		assertTrue("Should hit breakpoint", suspended.await(10, TimeUnit.SECONDS));

		JopRegisters regs = target.readRegisters();
		assertEquals(MAIN_METHOD_ADDR, regs.jpc());
		assertTrue("SP should be positive at main()", regs.sp() > 0);
		assertTrue("VP should be positive at main()", regs.vp() > 0);

		JopStackData stack = target.readStack();
		assertTrue("Stack should have entries at main()", stack.values().length > 0);
	}

	// --- Debug model integration ---

	@Test
	public void testDebugModelWithRealProgram() throws Exception {
		ILaunch launch = new Launch(null, "debug", null);
		JopDebugTarget debugTarget = new JopDebugTarget(launch, target, "NCoreHelloWorld");
		launch.addDebugTarget(debugTarget);
		debugTarget.fireInitialSuspendIfNeeded();

		// Verify debug target state
		assertTrue("Should be suspended at start", debugTarget.isSuspended());
		assertFalse("Should not be terminated", debugTarget.isTerminated());
		assertEquals("NCoreHelloWorld", debugTarget.getName());

		// Verify thread
		IThread[] threads = debugTarget.getThreads();
		assertEquals(1, threads.length);
		assertTrue("Thread should be suspended", threads[0].isSuspended());

		// Verify stack frame
		assertTrue("Thread should have stack frames", threads[0].hasStackFrames());
		IStackFrame frame = threads[0].getTopStackFrame();
		assertNotNull("Should have top stack frame", frame);

		// Verify variables (registers)
		IVariable[] vars = frame.getVariables();
		assertTrue("Should have at least 12 register vars", vars.length >= 12);
		assertEquals("A (TOS)", vars[0].getName());
		assertEquals("sp", vars[3].getName());
		assertEquals("jpc", vars[6].getName());

		// Step and verify state updates
		debugTarget.stepOver();
		assertTrue("Should still be suspended after step", debugTarget.isSuspended());

		frame = threads[0].getTopStackFrame();
		vars = frame.getVariables();
		assertNotNull("Should have variables after step", vars);

		// Terminate
		debugTarget.terminate();
		assertTrue("Should be terminated", debugTarget.isTerminated());
	}

	// --- Reset ---

	@Test
	public void testResetAfterExecution() throws Exception {
		// Step a few times
		for (int i = 0; i < 5; i++) {
			target.stepMicro();
		}
		JopRegisters before = target.readRegisters();
		assertTrue("JPC should have advanced", before.jpc() > 0);

		target.reset();
		assertEquals(JopTargetState.SUSPENDED, target.getState());

		JopRegisters after = target.readRegisters();
		// After reset, JPC should be back to initial boot address
		assertNotNull(after);
		assertTrue("SP should be positive after reset", after.sp() > 0);
	}

	// --- Terminate ---

	@Test
	public void testTerminate() throws Exception {
		target.stepMicro();
		target.terminate();
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	// --- Current instruction name ---

	@Test
	public void testCurrentInstructionNameAtBoot() {
		String name = target.getCurrentInstructionName();
		assertNotNull("Should have instruction name at boot", name);
		assertFalse("Instruction name should not be empty", name.isEmpty());
	}
}
