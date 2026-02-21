package com.jopdesign.core.sim.microcode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Microcode-level simulator for the JOP 65-instruction ISA.
 * <p>
 * Models the JOP hardware state: registers (pc, sp, vp, ar, jpc),
 * operand stack (A=TOS, B=NOS), internal stack, scratchpad memory,
 * external memory, multiply unit, and memory subsystem.
 * <p>
 * Supports step-by-step execution, breakpoints, and background resume.
 * Branch and jump instructions have 2 delay slots.
 */
public class MicrocodeSimulator {

	// Default sizes
	private static final int DEFAULT_STACK_SIZE = 1024;
	private static final int DEFAULT_SCRATCH_SIZE = 256;
	private static final int DEFAULT_MEM_SIZE = 65536;

	// IO addresses (negative memory addresses map to IO)
	private static final int IO_STATUS = -1;
	private static final int IO_UART_TX = -2;
	private static final int IO_UART_RX = -3;

	// State
	private MicrocodeProgram program;
	private SimulatorState state = SimulatorState.NOT_STARTED;

	// Registers
	private int pc;     // program counter
	private int sp;     // stack pointer
	private int vp;     // variable pointer (frame pointer)
	private int ar;     // address register
	private int jpc;    // Java program counter

	// Operand stack
	private int a;      // TOS (top of stack)
	private int b;      // NOS (next of stack)

	// Memory
	private int[] stack;
	private int[] scratchMem;
	private int[] mem;

	// Memory subsystem registers
	private int memReadAddr;
	private int memWriteAddr;
	private int memWriteData;
	private int memReadData;

	// Multiply unit
	private int mulA;
	private int mulB;
	private long mulResult;

	// Branch delay slot
	private int pendingPC = -1;
	private int delayCounter;

	// Bytecode operand (simulated, settable for testing)
	private int bytecodeOperand;
	private int bytecodeOperand16;
	private int bcStart;

	// Breakpoints (source line numbers)
	private final Set<Integer> breakpoints = new HashSet<>();

	// UART simulation
	private final Queue<Integer> uartInputQueue = new ArrayDeque<>();
	private boolean uartRxReady;

	// Listeners
	private final List<ISimulatorListener> listeners = new CopyOnWriteArrayList<>();

	// Execution count
	private long instructionsExecuted;

	// Background execution thread
	private volatile Thread executionThread;

	public MicrocodeSimulator() {
		this(DEFAULT_STACK_SIZE, DEFAULT_SCRATCH_SIZE, DEFAULT_MEM_SIZE);
	}

	public MicrocodeSimulator(int stackSize, int scratchSize, int memSize) {
		this.stack = new int[stackSize];
		this.scratchMem = new int[scratchSize];
		this.mem = new int[memSize];
	}

	// --- Program loading ---

	/** Load a program and reset all state. */
	public void load(MicrocodeProgram program) {
		this.program = program;
		reset();

		// Store constants into scratchpad at offset 32+
		int slot = MicrocodeParser.CONSTANT_OFFSET;
		for (var entry : program.constants().entrySet()) {
			if (slot < scratchMem.length) {
				scratchMem[slot++] = entry.getValue();
			}
		}
	}

	/** Reset all registers and memory to initial values. */
	private void reset() {
		pc = 0;
		sp = 0;
		vp = 0;
		ar = 0;
		jpc = 0;
		a = 0;
		b = 0;
		memReadAddr = 0;
		memWriteAddr = 0;
		memWriteData = 0;
		memReadData = 0;
		mulA = 0;
		mulB = 0;
		mulResult = 0;
		pendingPC = -1;
		delayCounter = 0;
		bytecodeOperand = 0;
		bytecodeOperand16 = 0;
		bcStart = 0;
		instructionsExecuted = 0;
		uartInputQueue.clear();
		uartRxReady = false;
		java.util.Arrays.fill(stack, 0);
		java.util.Arrays.fill(scratchMem, 0);
		java.util.Arrays.fill(mem, 0);
		state = SimulatorState.NOT_STARTED;
	}

	// --- Execution control ---

	/**
	 * Execute one instruction and advance the PC.
	 *
	 * @return true if execution can continue, false if terminated
	 */
	public boolean step() {
		if (program == null) return false;
		if (state == SimulatorState.TERMINATED) return false;

		if (state == SimulatorState.NOT_STARTED) {
			state = SimulatorState.RUNNING;
		}

		if (pc < 0 || pc >= program.statements().size()) {
			state = SimulatorState.TERMINATED;
			fireStateChanged(state);
			return false;
		}

		MicrocodeStatement stmt = program.statements().get(pc);

		// Capture delay counter before execution (branch/jump instructions set it)
		int delayBefore = delayCounter;

		executeInstruction(stmt);
		instructionsExecuted++;

		// Handle delay slot — only count down if delay was active BEFORE this step
		if (delayBefore > 0) {
			delayCounter--;
			if (delayCounter == 0 && pendingPC >= 0) {
				pc = pendingPC;
				pendingPC = -1;
			} else {
				pc++;
			}
		} else {
			pc++;
		}

		// Check for termination
		if (pc < 0 || pc >= program.statements().size()) {
			state = SimulatorState.TERMINATED;
			fireStateChanged(state);
			return false;
		}

		return true;
	}

	/**
	 * Resume execution on a background thread until breakpoint or termination.
	 */
	public void resume() {
		if (program == null) return;
		if (state == SimulatorState.TERMINATED) return;

		state = SimulatorState.RUNNING;
		fireStateChanged(state);

		executionThread = new Thread(() -> {
			try {
				while (state == SimulatorState.RUNNING) {
					if (!step()) break;

					// Check breakpoints after stepping
					if (state != SimulatorState.TERMINATED) {
						int currentLine = getCurrentSourceLine();
						if (breakpoints.contains(currentLine)) {
							state = SimulatorState.SUSPENDED;
							fireStateChanged(state);
							break;
						}
					}
				}
			} finally {
				executionThread = null;
			}
		}, "MicrocodeSimulator");
		executionThread.setDaemon(true);
		executionThread.start();
	}

	/** Suspend execution. */
	public void suspend() {
		if (state == SimulatorState.RUNNING) {
			state = SimulatorState.SUSPENDED;
			fireStateChanged(state);
		}
	}

	/** Terminate execution. */
	public void terminate() {
		state = SimulatorState.TERMINATED;
		Thread t = executionThread;
		if (t != null) {
			t.interrupt();
		}
		fireStateChanged(state);
	}

	/**
	 * Step one instruction synchronously, then suspend.
	 * Used for "Step Into" in the debugger.
	 */
	public void stepOver() {
		if (program == null) return;
		if (state == SimulatorState.TERMINATED) return;

		if (state == SimulatorState.NOT_STARTED) {
			state = SimulatorState.RUNNING;
		}

		step();

		if (state != SimulatorState.TERMINATED) {
			state = SimulatorState.SUSPENDED;
			fireStateChanged(state);
		}
	}

	// --- Instruction execution ---

	private void executeInstruction(MicrocodeStatement stmt) {
		String mnemonic = stmt.mnemonic();
		int operand = stmt.resolvedOperand();

		switch (mnemonic) {
			// --- Pop instructions ---
			case "pop" -> { a = b; b = stack[sp]; sp--; }
			case "and" -> { a = a & b; b = stack[sp]; sp--; }
			case "or"  -> { a = a | b; b = stack[sp]; sp--; }
			case "xor" -> { a = a ^ b; b = stack[sp]; sp--; }
			case "add" -> { a = a + b; b = stack[sp]; sp--; }
			case "sub" -> { a = b - a; b = stack[sp]; sp--; }

			case "st0"  -> { stack[vp] = a; a = b; b = stack[sp]; sp--; }
			case "st1"  -> { stack[vp + 1] = a; a = b; b = stack[sp]; sp--; }
			case "st2"  -> { stack[vp + 2] = a; a = b; b = stack[sp]; sp--; }
			case "st3"  -> { stack[vp + 3] = a; a = b; b = stack[sp]; sp--; }
			case "st"   -> { stack[vp + bytecodeOperand] = a; a = b; b = stack[sp]; sp--; }
			case "stmi" -> { stack[ar] = a; a = b; b = stack[sp]; sp--; }

			case "stvp"  -> { vp = a; a = b; b = stack[sp]; sp--; }
			case "stjpc" -> { jpc = a; a = b; b = stack[sp]; sp--; }
			case "star"  -> { ar = a; a = b; b = stack[sp]; sp--; }
			case "stsp"  -> { sp = a; a = b; b = stack[sp]; sp--; }

			case "ushr" -> { a = b >>> a; b = stack[sp]; sp--; }
			case "shl"  -> { a = b << a; b = stack[sp]; sp--; }
			case "shr"  -> { a = b >> a; b = stack[sp]; sp--; }

			case "stm" -> { scratchMem[operand] = a; a = b; b = stack[sp]; sp--; }

			case "stmul"  -> { mulA = a; mulB = b; mulResult = (long) a * (long) b; a = b; b = stack[sp]; sp--; }
			case "stmwa"  -> { memWriteAddr = a; a = b; b = stack[sp]; sp--; }
			case "stmra"  -> { memReadAddr = a; performMemoryRead(); a = b; b = stack[sp]; sp--; }
			case "stmwd"  -> { memWriteData = a; performMemoryWrite(); a = b; b = stack[sp]; sp--; }
			case "stald"  -> { a = b; b = stack[sp]; sp--; } // Simplified: array load start
			case "stast"  -> { a = b; b = stack[sp]; sp--; } // Simplified: array store start
			case "stgf"   -> { a = b; b = stack[sp]; sp--; } // Simplified: getfield start
			case "stpf"   -> { a = b; b = stack[sp]; sp--; } // Simplified: putfield start
			case "stcp"   -> { a = b; b = stack[sp]; sp--; } // Simplified: copy start
			case "stbcrd" -> { a = b; b = stack[sp]; sp--; } // Simplified: bytecode read
			case "stidx"  -> { a = b; b = stack[sp]; sp--; } // Simplified: index store
			case "stps"   -> { a = b; b = stack[sp]; sp--; } // Simplified: putstatic
			case "stmrac" -> { memReadAddr = a; performMemoryRead(); a = b; b = stack[sp]; sp--; }
			case "stmraf" -> { memReadAddr = a; performMemoryRead(); a = b; b = stack[sp]; sp--; }
			case "stmwdf" -> { memWriteData = a; performMemoryWrite(); a = b; b = stack[sp]; sp--; }
			case "stpfr"  -> { a = b; b = stack[sp]; sp--; } // Simplified: putfield ref

			// --- Branch instructions (pop-type with delay slots) ---
			case "bz" -> {
				boolean taken = (a == 0);
				a = b; b = stack[sp]; sp--;
				if (taken) {
					pendingPC = pc + operand + 1;
					delayCounter = 2;
				}
			}
			case "bnz" -> {
				boolean taken = (a != 0);
				a = b; b = stack[sp]; sp--;
				if (taken) {
					pendingPC = pc + operand + 1;
					delayCounter = 2;
				}
			}

			// --- Push instructions ---
			case "ldm" -> { sp++; stack[sp] = b; b = a; a = scratchMem[operand]; }
			case "ldi" -> { sp++; stack[sp] = b; b = a; a = scratchMem[operand + MicrocodeParser.CONSTANT_OFFSET]; }

			case "ldmrd"     -> { sp++; stack[sp] = b; b = a; a = memReadData; }
			case "ldmul"     -> { sp++; stack[sp] = b; b = a; a = (int) mulResult; }
			case "ldbcstart" -> { sp++; stack[sp] = b; b = a; a = bcStart; }

			case "ld0" -> { sp++; stack[sp] = b; b = a; a = stack[vp]; }
			case "ld1" -> { sp++; stack[sp] = b; b = a; a = stack[vp + 1]; }
			case "ld2" -> { sp++; stack[sp] = b; b = a; a = stack[vp + 2]; }
			case "ld3" -> { sp++; stack[sp] = b; b = a; a = stack[vp + 3]; }
			case "ld"  -> { sp++; stack[sp] = b; b = a; a = stack[vp + bytecodeOperand]; }
			case "ldmi" -> { sp++; stack[sp] = b; b = a; a = stack[ar]; }

			case "ldsp"  -> { sp++; stack[sp] = b; b = a; a = sp - 1; } // sp before push
			case "ldvp"  -> { sp++; stack[sp] = b; b = a; a = vp; }
			case "ldjpc" -> { sp++; stack[sp] = b; b = a; a = jpc; }

			case "ld_opd_8u"  -> { sp++; stack[sp] = b; b = a; a = bytecodeOperand & 0xFF; }
			case "ld_opd_8s"  -> { sp++; stack[sp] = b; b = a; a = (byte) bytecodeOperand; }
			case "ld_opd_16u" -> { sp++; stack[sp] = b; b = a; a = bytecodeOperand16 & 0xFFFF; }
			case "ld_opd_16s" -> { sp++; stack[sp] = b; b = a; a = (short) bytecodeOperand16; }

			case "dup" -> { sp++; stack[sp] = b; b = a; /* a stays the same */ }

			// --- NOP-type instructions ---
			case "nop"      -> { /* no operation */ }
			case "wait"     -> { /* instant in simulation */ }
			case "jbr"      -> { /* conditional bytecode branch - simplified nop */ }
			case "stgs"     -> { /* getstatic start - simplified nop */ }
			case "cinval"   -> { /* cache invalidate - nop in sim */ }
			case "atmstart" -> { /* atomic start - nop in sim */ }
			case "atmend"   -> { /* atomic end - nop in sim */ }

			// --- Jump instruction (with delay slots) ---
			case "jmp" -> {
				pendingPC = pc + operand + 1;
				delayCounter = 2;
			}

			default -> {
				// Unknown instruction — terminate
				state = SimulatorState.TERMINATED;
				fireStateChanged(state);
			}
		}
	}

	// --- Memory subsystem ---

	private void performMemoryRead() {
		if (memReadAddr < 0) {
			memReadData = readIO(memReadAddr);
		} else if (memReadAddr < mem.length) {
			memReadData = mem[memReadAddr];
		} else {
			memReadData = 0;
		}
	}

	private void performMemoryWrite() {
		if (memWriteAddr < 0) {
			writeIO(memWriteAddr, memWriteData);
		} else if (memWriteAddr < mem.length) {
			mem[memWriteAddr] = memWriteData;
		}
	}

	private int readIO(int addr) {
		return switch (addr) {
			case IO_STATUS -> {
				int status = 0x01; // TX ready
				if (!uartInputQueue.isEmpty()) {
					status |= 0x02; // RX ready
				}
				yield status;
			}
			case IO_UART_RX -> {
				Integer val = uartInputQueue.poll();
				yield val != null ? val : 0;
			}
			default -> 0;
		};
	}

	private void writeIO(int addr, int value) {
		if (addr == IO_UART_TX) {
			fireOutputProduced(String.valueOf((char) (value & 0xFF)));
		}
	}

	// --- Breakpoints ---

	public void addBreakpoint(int sourceLine) {
		breakpoints.add(sourceLine);
	}

	public void removeBreakpoint(int sourceLine) {
		breakpoints.remove(sourceLine);
	}

	public Set<Integer> getBreakpoints() {
		return Set.copyOf(breakpoints);
	}

	// --- UART input ---

	/** Queue input characters for UART RX simulation. */
	public void provideInput(String text) {
		for (char c : text.toCharArray()) {
			uartInputQueue.add((int) c);
		}
	}

	// --- Bytecode operand (for ld_opd_* and st/ld instructions) ---

	public void setBytecodeOperand(int value) {
		this.bytecodeOperand = value;
	}

	public void setBytecodeOperand16(int value) {
		this.bytecodeOperand16 = value;
	}

	public void setBcStart(int value) {
		this.bcStart = value;
	}

	// --- State inspection ---

	public SimulatorState getState() { return state; }
	public int getPC() { return pc; }
	public int getSP() { return sp; }
	public int getVP() { return vp; }
	public int getAR() { return ar; }
	public int getJPC() { return jpc; }
	public int getA() { return a; }
	public int getB() { return b; }
	public long getInstructionsExecuted() { return instructionsExecuted; }

	public int getStackValue(int index) {
		if (index >= 0 && index < stack.length) return stack[index];
		return 0;
	}

	public int getScratchValue(int index) {
		if (index >= 0 && index < scratchMem.length) return scratchMem[index];
		return 0;
	}

	public int getMemValue(int addr) {
		if (addr >= 0 && addr < mem.length) return mem[addr];
		return 0;
	}

	public int getMemReadData() { return memReadData; }

	/** Get the 1-based source line for the current PC. */
	public int getCurrentSourceLine() {
		if (program == null || pc < 0 || pc >= program.statements().size()) return -1;
		return program.statements().get(pc).sourceLine();
	}

	/** Get the current statement at PC, or null if terminated. */
	public MicrocodeStatement getCurrentStatement() {
		if (program == null || pc < 0 || pc >= program.statements().size()) return null;
		return program.statements().get(pc);
	}

	public MicrocodeProgram getProgram() { return program; }

	public int getStackSize() { return stack.length; }
	public int getMemSize() { return mem.length; }
	public int getScratchSize() { return scratchMem.length; }

	// --- Direct state setters (for testing) ---

	public void setA(int value) { this.a = value; }
	public void setB(int value) { this.b = value; }
	public void setSP(int value) { this.sp = value; }
	public void setVP(int value) { this.vp = value; }
	public void setPC(int value) { this.pc = value; }
	public void setStackValue(int index, int value) { stack[index] = value; }
	public void setMemValue(int addr, int value) { mem[addr] = value; }
	public void setScratchValue(int index, int value) { scratchMem[index] = value; }

	// --- Listeners ---

	public void addListener(ISimulatorListener listener) {
		listeners.add(listener);
	}

	public void removeListener(ISimulatorListener listener) {
		listeners.remove(listener);
	}

	private void fireStateChanged(SimulatorState newState) {
		for (ISimulatorListener l : listeners) {
			l.stateChanged(newState);
		}
	}

	private void fireOutputProduced(String text) {
		for (ISimulatorListener l : listeners) {
			l.outputProduced(text);
		}
	}
}
