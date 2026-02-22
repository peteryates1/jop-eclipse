package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.IJopTargetListener;
import com.jopdesign.core.sim.JopBreakpointInfo;
import com.jopdesign.core.sim.JopBreakpointType;
import com.jopdesign.core.sim.JopMemoryData;
import com.jopdesign.core.sim.JopRegister;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopStackData;
import com.jopdesign.core.sim.JopSuspendReason;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.JopTargetInfo;
import com.jopdesign.core.sim.JopTargetState;
import com.jopdesign.core.sim.SimulatorJopTarget;
import com.jopdesign.core.sim.microcode.MicrocodeParser;
import com.jopdesign.core.sim.microcode.MicrocodeParser.MicrocodeParseException;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;

/**
 * Tests for {@link SimulatorJopTarget}.
 */
public class SimulatorJopTargetTest {

	private MicrocodeParser parser;
	private SimulatorJopTarget target;

	@Before
	public void setUp() throws MicrocodeParseException, JopTargetException {
		parser = new MicrocodeParser();
		String source = """
				val1 = 10
				val2 = 20
				start:
				ldi val1
				ldi val2
				add
				nop
				nop
				""";
		MicrocodeProgram prog = parser.parse(source);
		target = new SimulatorJopTarget(prog, 1024, 1024, 64);
		target.connect();
	}

	@Test
	public void testNameAndTypeId() {
		assertEquals("JOP Simulator", target.getName());
		assertEquals("simulator", target.getTargetTypeId());
	}

	@Test
	public void testInitialState() {
		assertEquals(JopTargetState.NOT_STARTED, target.getState());
	}

	@Test
	public void testStepMicro() throws JopTargetException {
		target.stepMicro(); // ldi val1
		JopRegisters regs = target.readRegisters();
		assertEquals(10, regs.a());
	}

	@Test
	public void testStepMicroMultiple() throws JopTargetException {
		target.stepMicro(); // ldi val1: A=10
		target.stepMicro(); // ldi val2: A=20, B=10
		target.stepMicro(); // add: A=30
		JopRegisters regs = target.readRegisters();
		assertEquals(30, regs.a());
	}

	@Test
	public void testReadRegisters() throws JopTargetException {
		JopRegisters regs = target.readRegisters();
		assertNotNull(regs);
		assertEquals(64, regs.sp()); // initial SP
	}

	@Test
	public void testReadStack() throws JopTargetException {
		target.stepMicro(); // push 10
		target.stepMicro(); // push 20
		JopStackData stack = target.readStack();
		assertNotNull(stack);
		assertTrue(stack.values().length > 0);
	}

	@Test
	public void testReadMemory() throws JopTargetException {
		JopMemoryData mem = target.readMemory(0, 10);
		assertNotNull(mem);
		assertEquals(0, mem.startAddress());
		assertEquals(10, mem.values().length);
	}

	@Test
	public void testWriteRegister() throws JopTargetException {
		target.writeRegister(JopRegister.A, 99);
		JopRegisters regs = target.readRegisters();
		assertEquals(99, regs.a());
	}

	@Test
	public void testWriteMemory() throws JopTargetException {
		target.writeMemory(50, 12345);
		JopMemoryData mem = target.readMemory(50, 1);
		assertEquals(12345, mem.values()[0]);
	}

	@Test(expected = JopTargetException.class)
	public void testWriteReadOnlyRegister() throws JopTargetException {
		target.writeRegister(JopRegister.MEM_RD_DATA, 0);
	}

	@Test
	public void testSetBreakpointByAddress() throws JopTargetException, InterruptedException {
		// Line 6 is "add", which is statement index 2 (0-based)
		int addr = target.resolveLineToAddress(6);
		assertTrue("Should resolve line 6 to a valid address", addr >= 0);
		int slot = target.setBreakpoint(JopBreakpointType.MICRO_PC, addr);
		assertTrue(slot >= 0);
		target.resume();
		Thread.sleep(300);
		assertEquals(JopTargetState.SUSPENDED, target.getState());
		assertEquals(6, target.getCurrentSourceLine());
	}

	@Test
	public void testClearBreakpoint() throws JopTargetException, InterruptedException {
		int addr = target.resolveLineToAddress(6);
		int slot = target.setBreakpoint(JopBreakpointType.MICRO_PC, addr);
		target.clearBreakpoint(slot);
		target.resume();
		Thread.sleep(300);
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	@Test
	public void testGetBreakpoints() throws JopTargetException {
		int addr = target.resolveLineToAddress(6);
		int slot = target.setBreakpoint(JopBreakpointType.MICRO_PC, addr);
		JopBreakpointInfo[] bps = target.getBreakpoints();
		assertEquals(1, bps.length);
		assertEquals(slot, bps[0].slot());
		assertEquals(JopBreakpointType.MICRO_PC, bps[0].type());
		assertEquals(addr, bps[0].address());
	}

	@Test(expected = JopTargetException.class)
	public void testSetJpcBreakpointNotSupported() throws JopTargetException {
		target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, 0);
	}

	@Test
	public void testResumeRunsToEnd() throws JopTargetException, InterruptedException {
		target.resume();
		Thread.sleep(300);
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	@Test
	public void testTerminate() throws JopTargetException {
		target.terminate();
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	@Test
	public void testGetCurrentSourceLine() throws JopTargetException {
		assertEquals(4, target.getCurrentSourceLine()); // line 4 = "ldi val1"
		target.stepMicro();
		assertEquals(5, target.getCurrentSourceLine()); // line 5 = "ldi val2"
	}

	@Test
	public void testGetCurrentInstructionName() throws JopTargetException {
		assertEquals("ldi", target.getCurrentInstructionName());
		target.stepMicro();
		assertEquals("ldi", target.getCurrentInstructionName());
		target.stepMicro();
		assertEquals("add", target.getCurrentInstructionName());
	}

	@Test
	public void testListenerStateChanges() throws JopTargetException {
		List<JopTargetState> states = new ArrayList<>();
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				states.add(newState);
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.stepMicro();
		assertTrue(states.contains(JopTargetState.SUSPENDED));
	}

	@Test
	public void testListenerSuspendReason() throws JopTargetException {
		List<JopSuspendReason> reasons = new ArrayList<>();
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				if (reason != null) {
					reasons.add(reason);
				}
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.stepMicro();
		assertTrue(reasons.contains(JopSuspendReason.STEP_COMPLETE));
	}

	@Test
	public void testListenerOutput() throws MicrocodeParseException, JopTargetException {
		String source = """
				tx_addr = -2
				char_B = 66
				ldi char_B
				ldi tx_addr
				stmwa
				stmwd
				""";
		MicrocodeProgram prog = parser.parse(source);
		SimulatorJopTarget outputTarget = new SimulatorJopTarget(prog, 1024, 1024, 64);
		outputTarget.connect();

		List<String> output = new ArrayList<>();
		outputTarget.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
			}

			@Override
			public void outputProduced(String text) {
				output.add(text);
			}
		});

		for (int i = 0; i < 4; i++) {
			outputTarget.stepMicro();
		}
		assertEquals(1, output.size());
		assertEquals("B", output.get(0));
	}

	@Test
	public void testDisconnect() throws JopTargetException {
		target.disconnect();
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	@Test
	public void testStepBytecode() throws JopTargetException {
		// stepBytecode loops until jpc changes; since jpc doesn't change in basic
		// microcode, it will run until termination
		target.stepBytecode();
		// After bytecode step, should still be in a valid state
		JopTargetState state = target.getState();
		assertTrue(state == JopTargetState.SUSPENDED || state == JopTargetState.TERMINATED);
	}

	@Test
	public void testProvideInput() throws JopTargetException {
		// Should not throw
		target.provideInput("test");
	}

	@Test
	public void testRemoveListener() throws JopTargetException {
		List<JopTargetState> states = new ArrayList<>();
		IJopTargetListener listener = new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				states.add(newState);
			}

			@Override
			public void outputProduced(String text) {
			}
		};

		target.addListener(listener);
		target.removeListener(listener);
		target.stepMicro();
		// Listener was removed, so states should only have the simulator's direct events
		// (which go through the internal listener, not our removed one)
		assertTrue(states.isEmpty());
	}

	@Test
	public void testConnectTwice() throws JopTargetException {
		// Second connect should reset cleanly
		target.connect();
		JopRegisters regs = target.readRegisters();
		assertEquals(64, regs.sp());
	}

	@Test
	public void testReadRegistersAfterDisconnect() throws JopTargetException {
		target.disconnect();
		// Simulator still exists after disconnect, but state is TERMINATED
		// readRegisters should still work (reads from terminated simulator)
		JopRegisters regs = target.readRegisters();
		assertNotNull(regs);
	}

	@Test
	public void testReset() throws JopTargetException {
		target.stepMicro(); // ldi val1: A=10
		target.stepMicro(); // ldi val2: A=20
		JopRegisters before = target.readRegisters();
		assertTrue(before.pc() > 0);

		List<JopSuspendReason> reasons = new ArrayList<>();
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				if (reason != null) reasons.add(reason);
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.reset();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
		JopRegisters after = target.readRegisters();
		assertEquals(0, after.pc());
		assertEquals(64, after.sp());
		assertTrue(reasons.contains(JopSuspendReason.RESET));
	}

	@Test
	public void testGetTargetInfo() {
		JopTargetInfo info = target.getTargetInfo();
		assertNotNull(info);
		assertEquals(1, info.numCores());
		assertEquals(Integer.MAX_VALUE, info.numBreakpoints());
		assertEquals(1024, info.stackDepth());
		assertEquals(1024, info.memorySize());
		assertEquals("simulator", info.version());
		assertEquals(1, info.protocolMajor());
		assertEquals(0, info.protocolMinor());
		assertEquals(0, info.extendedRegistersMask());
	}

	@Test
	public void testWriteMemoryBlock() throws JopTargetException {
		int[] values = { 100, 200, 300 };
		target.writeMemoryBlock(50, values);
		JopMemoryData mem = target.readMemory(50, 3);
		assertEquals(100, mem.values()[0]);
		assertEquals(200, mem.values()[1]);
		assertEquals(300, mem.values()[2]);
	}

	@Test
	public void testExtendedRegistersAreZero() throws JopTargetException {
		JopRegisters regs = target.readRegisters();
		assertEquals(0, regs.flags());
		assertEquals(0, regs.instr());
		assertEquals(0, regs.jopd());
	}

	@Test
	public void testResolveLineToAddress() {
		// Line 4 = "ldi val1", should be statement 0
		int addr = target.resolveLineToAddress(4);
		assertEquals(0, addr);

		// Line 5 = "ldi val2", should be statement 1
		addr = target.resolveLineToAddress(5);
		assertEquals(1, addr);

		// Non-existent line
		addr = target.resolveLineToAddress(999);
		assertEquals(-1, addr);
	}
}
