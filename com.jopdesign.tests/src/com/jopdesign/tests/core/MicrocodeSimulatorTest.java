package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.microcode.ISimulatorListener;
import com.jopdesign.core.sim.microcode.MicrocodeParser;
import com.jopdesign.core.sim.microcode.MicrocodeParser.MicrocodeParseException;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;
import com.jopdesign.core.sim.microcode.MicrocodeSimulator;
import com.jopdesign.core.sim.microcode.SimulatorState;

/**
 * Tests for {@link MicrocodeSimulator}.
 */
public class MicrocodeSimulatorTest {

	private MicrocodeParser parser;
	private MicrocodeSimulator sim;

	@Before
	public void setUp() {
		parser = new MicrocodeParser();
		sim = new MicrocodeSimulator();
	}

	private void loadAndRun(String source) throws MicrocodeParseException {
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);
		while (sim.step()) {
			// run until terminated
		}
	}

	@Test
	public void testPushAndPop() throws MicrocodeParseException {
		// Push two values using ldi with constants, then pop
		String source = """
				val1 = 42
				val2 = 7
				ldi val1
				ldi val2
				pop
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		// Execute ldi val1: pushes 42 onto A
		sim.step();
		assertEquals(42, sim.getA());

		// Execute ldi val2: pushes 7 onto A, 42 moves to B
		sim.step();
		assertEquals(7, sim.getA());
		assertEquals(42, sim.getB());

		// Execute pop: A=B=42, B=stack[sp]
		sim.step();
		assertEquals(42, sim.getA());
	}

	@Test
	public void testAddition() throws MicrocodeParseException {
		String source = """
				val1 = 10
				val2 = 20
				ldi val1
				ldi val2
				add
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // ldi val1: A=10
		sim.step(); // ldi val2: A=20, B=10
		sim.step(); // add: A=30
		assertEquals(30, sim.getA());
	}

	@Test
	public void testSubtraction() throws MicrocodeParseException {
		// sub computes B - A
		String source = """
				val1 = 30
				val2 = 10
				ldi val1
				ldi val2
				sub
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // A=30
		sim.step(); // A=10, B=30
		sim.step(); // A = B-A = 30-10 = 20
		assertEquals(20, sim.getA());
	}

	@Test
	public void testBitwiseOperations() throws MicrocodeParseException {
		String source = """
				val1 = 0xFF
				val2 = 0x0F
				ldi val1
				ldi val2
				and
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // A=0xFF
		sim.step(); // A=0x0F, B=0xFF
		sim.step(); // A = 0xFF & 0x0F = 0x0F
		assertEquals(0x0F, sim.getA());
	}

	@Test
	public void testBranchWithDelaySlots() throws MicrocodeParseException {
		// bz should have 2 delay slots: next 2 instructions always execute
		String source = """
				val0 = 0
				val1 = 1
				val2 = 2
				ldi val0
				bz target      // branch taken (A==0), delay slots follow
				ldi val1        // delay slot 1 - always executes
				ldi val2        // delay slot 2 - always executes
				target:
				nop
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // ldi val0: A=0
		sim.step(); // bz target: taken (A==0), sets pending PC, pops
		sim.step(); // delay slot 1: ldi val1: A=1
		sim.step(); // delay slot 2: ldi val2: A=2, then PC jumps to target
		assertEquals(2, sim.getA());
		// PC should now be at target (statement index 6, the nop)
		assertEquals("nop", sim.getCurrentStatement().mnemonic());
	}

	@Test
	public void testBranchNotTaken() throws MicrocodeParseException {
		String source = """
				val1 = 5
				ldi val1
				bz skip         // not taken (A=5 != 0)
				nop
				nop
				skip:
				nop
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // ldi val1: A=5
		sim.step(); // bz skip: NOT taken (A=5), pops
		// Should continue sequentially to the nop after bz
		sim.step(); // nop (statement 2)
		sim.step(); // nop (statement 3)
		sim.step(); // nop at skip (statement 4)
		assertEquals(SimulatorState.TERMINATED, sim.getState());
	}

	@Test
	public void testJmpWithDelaySlots() throws MicrocodeParseException {
		String source = """
				val1 = 99
				jmp target
				nop            // delay slot 1
				nop            // delay slot 2
				nop            // should be skipped
				target:
				ldi val1
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // jmp target: sets pending PC
		sim.step(); // delay slot 1: nop
		sim.step(); // delay slot 2: nop, then PC jumps to target
		sim.step(); // ldi val1 at target: A=99
		assertEquals(99, sim.getA());
	}

	@Test
	public void testMemoryReadWrite() throws MicrocodeParseException {
		String source = """
				addr = 100
				data = 42
				ldi data
				ldi addr
				stmwa          // memWriteAddr = 100
				stmwd          // memWriteData = 42, writes mem[100]=42
				ldi addr
				stmra          // memReadAddr = 100, reads mem[100]
				ldmrd          // A = memReadData = 42
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		// Run all instructions
		for (int i = 0; i < 7; i++) sim.step();

		assertEquals(42, sim.getA());
	}

	@Test
	public void testScratchMemory() throws MicrocodeParseException {
		String source = """
				val = 77
				ldi val
				stm 5          // store A=77 to stack[5]
				ldm 5          // load stack[5]=77
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // ldi val: A=77
		sim.step(); // stm 5: store 77 at stack[5], pop
		sim.step(); // ldm 5: push stack[5]=77 onto A
		assertEquals(77, sim.getA());
	}

	@Test
	public void testBreakpoints() throws MicrocodeParseException {
		String source = """
				nop
				nop
				nop
				nop
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		// Add breakpoint at line 3 (the third nop)
		sim.addBreakpoint(3);

		// Resume should run and stop at the breakpoint
		sim.resume();

		// Wait for the simulator thread to complete
		try { Thread.sleep(200); } catch (InterruptedException e) { /* ignore */ }

		assertEquals(SimulatorState.SUSPENDED, sim.getState());
		assertEquals(3, sim.getCurrentSourceLine());
	}

	@Test
	public void testStepMode() throws MicrocodeParseException {
		String source = """
				nop
				nop
				nop
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.stepOver();
		assertEquals(SimulatorState.SUSPENDED, sim.getState());
		assertEquals(2, sim.getCurrentSourceLine()); // After first nop, now at line 2
	}

	@Test
	public void testRegisterStoreLoad() throws MicrocodeParseException {
		String source = """
				val = 50
				ldi val
				stvp           // vp = 50
				ldvp           // A = 50
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // ldi val: A=50
		sim.step(); // stvp: vp=50, pop
		assertEquals(50, sim.getVP());

		sim.step(); // ldvp: A=50
		assertEquals(50, sim.getA());
	}

	@Test
	public void testTerminationAtEnd() throws MicrocodeParseException {
		String source = "nop\nnop";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step();
		sim.step();
		assertEquals(SimulatorState.TERMINATED, sim.getState());
	}

	@Test
	public void testShiftOperations() throws MicrocodeParseException {
		String source = """
				val = 8
				shift = 2
				ldi val
				ldi shift
				shl
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // A=8
		sim.step(); // A=2, B=8
		sim.step(); // shl: A = B << A = 8 << 2 = 32
		assertEquals(32, sim.getA());
	}

	@Test
	public void testMultiply() throws MicrocodeParseException {
		String source = """
				val1 = 6
				val2 = 7
				ldi val1
				ldi val2
				stmul          // mulA=7, mulB=6, result=42
				ldmul          // A = 42
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // A=6
		sim.step(); // A=7, B=6
		sim.step(); // stmul: mulA=7, mulB=6, pop
		sim.step(); // ldmul: A=42
		assertEquals(42, sim.getA());
	}

	@Test
	public void testDup() throws MicrocodeParseException {
		String source = """
				val = 33
				ldi val
				dup
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); // ldi val: A=33
		sim.step(); // dup: A=33, B=33
		assertEquals(33, sim.getA());
		assertEquals(33, sim.getB());
	}

	@Test
	public void testUartOutput() throws MicrocodeParseException {
		List<String> output = new ArrayList<>();
		sim.addListener(new ISimulatorListener() {
			@Override
			public void stateChanged(SimulatorState newState) {}
			@Override
			public void outputProduced(String text) { output.add(text); }
		});

		String source = """
				tx_addr = -2
				char_A = 65
				ldi char_A
				ldi tx_addr
				stmwa          // memWriteAddr = -2 (UART TX)
				stmwd          // writes 65 ('A') to UART
				""";
		MicrocodeProgram prog = parser.parse(source);
		sim.load(prog);

		sim.step(); sim.step(); sim.step(); sim.step();
		assertEquals(1, output.size());
		assertEquals("A", output.get(0));
	}

	@Test
	public void testStateListener() throws MicrocodeParseException {
		List<SimulatorState> states = new ArrayList<>();
		sim.addListener(new ISimulatorListener() {
			@Override
			public void stateChanged(SimulatorState newState) { states.add(newState); }
			@Override
			public void outputProduced(String text) {}
		});

		MicrocodeProgram prog = parser.parse("nop");
		sim.load(prog);
		sim.stepOver();

		assertTrue(states.contains(SimulatorState.TERMINATED));
	}
}
