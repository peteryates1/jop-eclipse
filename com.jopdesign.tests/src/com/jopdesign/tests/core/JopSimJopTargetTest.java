package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.IJopTargetListener;
import com.jopdesign.core.sim.JopBreakpointInfo;
import com.jopdesign.core.sim.JopBreakpointType;
import com.jopdesign.core.sim.JopMemoryData;
import com.jopdesign.core.sim.JopRegister;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopSimJopTarget;
import com.jopdesign.core.sim.JopStackData;
import com.jopdesign.core.sim.JopSuspendReason;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.JopTargetInfo;
import com.jopdesign.core.sim.JopTargetState;

/**
 * Tests for {@link JopSimJopTarget}.
 *
 * <p>Uses a synthetic .jop file with a minimal boot method containing:
 * nop, iconst_1, iconst_2, iadd, followed by nops.
 *
 * <p>Memory layout:
 * <pre>
 * mem[0]  = 16        // instruction word count (informational)
 * mem[1]  = 2         // pointer to boot class struct
 * mem[2]  = 6         // CP entry → method struct at addr 6
 * mem[3]  = 200       // jjp (JVM handler table, unused for simple opcodes)
 * mem[4]  = 0         // jjhp
 * mem[5]  = 0         // padding
 * mem[6]  = (8<<10)|4 // method: start=8, len=4 words
 * mem[7]  = (10<<10)  // cp=10, locals=0, args=0
 * mem[8]  = bytecodes: nop(0x00), iconst_1(0x04), iconst_2(0x05), iadd(0x60)
 * mem[9]  = 0         // nops
 * mem[10] = 0         // nops
 * mem[11] = 0         // nops
 * </pre>
 *
 * <p>After start(): sp=69, vp=65, mp=6, cp=10, pc=0
 * JPC = methodStart(8) + corrPc(pc)
 */
public class JopSimJopTargetTest {

	private File jopFile;
	private JopSimJopTarget target;

	@Before
	public void setUp() throws Exception {
		jopFile = createTestJopFile();
		target = new JopSimJopTarget(jopFile.getAbsolutePath(), null);
		target.connect();
	}

	@After
	public void tearDown() throws Exception {
		if (target != null && target.getState() != JopTargetState.TERMINATED) {
			target.disconnect();
		}
		if (jopFile != null) {
			jopFile.delete();
		}
	}

	@Test
	public void testNameAndTypeId() {
		assertEquals("JOP Bytecode Simulator", target.getName());
		assertEquals("jopsim", target.getTargetTypeId());
	}

	@Test
	public void testConnectAndDisconnect() throws JopTargetException {
		assertEquals(JopTargetState.SUSPENDED, target.getState());
		target.disconnect();
		assertEquals(JopTargetState.TERMINATED, target.getState());
	}

	@Test
	public void testReadRegistersInitial() throws JopTargetException {
		JopRegisters regs = target.readRegisters();
		assertNotNull(regs);
		// After start(): sp=69 (STACK_OFF=64 + 5 frame words)
		assertEquals(69, regs.sp());
		// JPC = methodStart(8) + corrPc(0) = 8
		assertEquals(8, regs.jpc());
		// PC is always 0 (no microcode)
		assertEquals(0, regs.pc());
	}

	@Test
	public void testStepMicro() throws JopTargetException {
		// Step over nop
		target.stepMicro();
		assertEquals(JopTargetState.SUSPENDED, target.getState());

		JopRegisters regs = target.readRegisters();
		// JPC should advance: 8 + 1 = 9
		assertEquals(9, regs.jpc());
	}

	@Test
	public void testStepMicroExecutesIconst() throws JopTargetException {
		target.stepMicro(); // nop
		target.stepMicro(); // iconst_1: pushes 1

		JopRegisters regs = target.readRegisters();
		assertEquals(1, regs.a()); // TOS = 1
		assertEquals(70, regs.sp()); // SP incremented
	}

	@Test
	public void testStepMicroExecutesIadd() throws JopTargetException {
		target.stepMicro(); // nop
		target.stepMicro(); // iconst_1: TOS=1
		target.stepMicro(); // iconst_2: TOS=2, NOS=1
		target.stepMicro(); // iadd: TOS=3

		JopRegisters regs = target.readRegisters();
		assertEquals(3, regs.a()); // 1 + 2 = 3
		assertEquals(70, regs.sp()); // SP back down after iadd
	}

	@Test
	public void testStepBytecodeIsSameAsStepMicro() throws JopTargetException {
		// JopSim has no microcode, both steps execute one bytecode
		target.stepBytecode(); // nop
		JopRegisters regs = target.readRegisters();
		assertEquals(9, regs.jpc()); // Advanced by 1
	}

	@Test
	public void testResumeAndSuspend() throws JopTargetException, InterruptedException {
		target.resume();
		assertEquals(JopTargetState.RUNNING, target.getState());

		Thread.sleep(50);
		target.suspend();
		assertEquals(JopTargetState.SUSPENDED, target.getState());
	}

	@Test
	public void testBreakpoint() throws JopTargetException, InterruptedException {
		// Set breakpoint at JPC=10 (after nop and iconst_1)
		int slot = target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, 10);
		assertTrue(slot >= 0);

		target.resume();
		Thread.sleep(200);

		assertEquals(JopTargetState.SUSPENDED, target.getState());
		JopRegisters regs = target.readRegisters();
		assertEquals(10, regs.jpc());
	}

	@Test
	public void testClearBreakpoint() throws JopTargetException, InterruptedException {
		// Set and immediately clear a breakpoint
		int slot = target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, 10);
		target.clearBreakpoint(slot);

		// Resume - should run past address 10 without stopping
		target.resume();
		Thread.sleep(200);
		target.suspend();

		JopRegisters regs = target.readRegisters();
		// JPC should have advanced well past 10
		assertTrue("JPC should be past 10 after clearing breakpoint",
				regs.jpc() > 10);
	}

	@Test
	public void testGetBreakpoints() throws JopTargetException {
		int slot1 = target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, 10);
		int slot2 = target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, 12);

		JopBreakpointInfo[] bps = target.getBreakpoints();
		assertEquals(2, bps.length);

		target.clearBreakpoint(slot1);
		bps = target.getBreakpoints();
		assertEquals(1, bps.length);
		assertEquals(slot2, bps[0].slot());
	}

	@Test
	public void testReset() throws JopTargetException {
		// Step a few times
		target.stepMicro();
		target.stepMicro();
		JopRegisters before = target.readRegisters();
		assertTrue(before.jpc() > 8);

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
		assertEquals(8, after.jpc()); // Back to initial JPC
		assertEquals(69, after.sp()); // Back to initial SP
		assertTrue(reasons.contains(JopSuspendReason.RESET));
	}

	@Test
	public void testReadStack() throws JopTargetException {
		JopStackData stack = target.readStack();
		assertNotNull(stack);
		assertEquals(69, stack.sp());
		assertTrue(stack.values().length > 0);
	}

	@Test
	public void testReadMemory() throws JopTargetException {
		// Read the method struct area
		JopMemoryData mem = target.readMemory(6, 2);
		assertNotNull(mem);
		assertEquals(6, mem.startAddress());
		assertEquals(2, mem.values().length);
		// mem[6] = (8 << 10) | 4 = 8196
		assertEquals(8196, mem.values()[0]);
	}

	@Test
	public void testWriteMemory() throws JopTargetException {
		target.writeMemory(100, 42);
		JopMemoryData mem = target.readMemory(100, 1);
		assertEquals(42, mem.values()[0]);
	}

	@Test
	public void testWriteRegister() throws JopTargetException {
		target.writeRegister(JopRegister.SP, 80);
		JopRegisters regs = target.readRegisters();
		assertEquals(80, regs.sp());
	}

	@Test(expected = JopTargetException.class)
	public void testWriteReadOnlyRegister() throws JopTargetException {
		target.writeRegister(JopRegister.MEM_RD_DATA, 0);
	}

	@Test
	public void testGetTargetInfo() {
		JopTargetInfo info = target.getTargetInfo();
		assertNotNull(info);
		assertEquals(1, info.numCores());
		assertEquals(4, info.numBreakpoints());
		assertEquals(64 * 1024, info.stackDepth());
		assertEquals(1024 * 1024, info.memorySize());
		assertEquals("jopsim", info.version());
		assertEquals(1, info.protocolMajor());
		assertEquals(0, info.protocolMinor());
		assertEquals(0, info.extendedRegistersMask());
	}

	@Test(expected = JopTargetException.class)
	public void testMicroPcBreakpointRejected() throws JopTargetException {
		target.setBreakpoint(JopBreakpointType.MICRO_PC, 0);
	}

	@Test
	public void testWriteMemoryBlock() throws JopTargetException {
		int[] values = { 111, 222, 333 };
		target.writeMemoryBlock(100, values);
		JopMemoryData mem = target.readMemory(100, 3);
		assertEquals(111, mem.values()[0]);
		assertEquals(222, mem.values()[1]);
		assertEquals(333, mem.values()[2]);
	}

	@Test
	public void testExtendedRegistersAreZero() throws JopTargetException {
		JopRegisters regs = target.readRegisters();
		assertEquals(0, regs.flags());
		assertEquals(0, regs.instr());
		assertEquals(0, regs.jopd());
	}

	@Test
	public void testBreakpointSlotInListener() throws JopTargetException, InterruptedException {
		// Set breakpoint at JPC=10
		int slot = target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, 10);

		List<Integer> hitSlots = new ArrayList<>();
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				if (reason == JopSuspendReason.BREAKPOINT) {
					hitSlots.add(breakpointSlot);
				}
			}

			@Override
			public void outputProduced(String text) {
			}
		});

		target.resume();
		Thread.sleep(200);

		assertEquals(JopTargetState.SUSPENDED, target.getState());
		assertEquals(1, hitSlots.size());
		assertEquals(slot, (int) hitSlots.get(0));
	}

	@Test
	public void testListenerSuspendReasons() throws JopTargetException {
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

		target.stepMicro();
		assertTrue("Should fire STEP_COMPLETE", reasons.contains(JopSuspendReason.STEP_COMPLETE));
	}

	@Test
	public void testResolveLineToAddress() {
		// JopSim targets return -1 (no source mapping at bytecode level)
		assertEquals(-1, target.resolveLineToAddress(1));
	}

	@Test
	public void testGetCurrentInstructionName() throws JopTargetException {
		// Initial instruction is nop (bytecode 0x00)
		String name = target.getCurrentInstructionName();
		assertNotNull(name);
		assertEquals("nop", name);
	}

	// --- Helper ---

	/**
	 * Creates a minimal synthetic .jop file that JopSim can boot.
	 * The boot method contains: nop, iconst_1, iconst_2, iadd, then nops.
	 */
	private File createTestJopFile() throws IOException {
		File f = File.createTempFile("test-jopsim-", ".jop");
		try (FileWriter w = new FileWriter(f)) {
			// Bytecodes packed into word (big-endian):
			// byte[0]=nop(0), byte[1]=iconst_1(4), byte[2]=iconst_2(5), byte[3]=iadd(0x60)
			int bytecodeWord = (0x00 << 24) | (0x04 << 16) | (0x05 << 8) | 0x60;

			w.write("16\n");                // mem[0]: instruction words (informational)
			w.write("2\n");                 // mem[1]: pointer to boot entry
			w.write("6\n");                 // mem[2]: CP entry → method struct at 6
			w.write("200\n");               // mem[3]: jjp (handler table, unused here)
			w.write("0\n");                 // mem[4]: jjhp
			w.write("0\n");                 // mem[5]: padding
			w.write((8 << 10 | 4) + "\n");  // mem[6]: method start=8, len=4 words
			w.write((10 << 10) + "\n");     // mem[7]: cp=10, locals=0, args=0
			w.write(bytecodeWord + "\n");   // mem[8]: nop, iconst_1, iconst_2, iadd
			w.write("0\n");                 // mem[9]: nops
			w.write("0\n");                 // mem[10]: nops
			w.write("0\n");                 // mem[11]: nops
		}
		return f;
	}
}
