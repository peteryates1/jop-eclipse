package com.jopdesign.core.sim;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jopdesign.core.sim.microcode.ISimulatorListener;
import com.jopdesign.core.sim.microcode.MicrocodeProgram;
import com.jopdesign.core.sim.microcode.MicrocodeSimulator;
import com.jopdesign.core.sim.microcode.MicrocodeStatement;
import com.jopdesign.core.sim.microcode.SimulatorState;

/**
 * IJopTarget implementation that wraps a {@link MicrocodeSimulator}.
 */
public class SimulatorJopTarget implements IJopTarget {

	private final MicrocodeProgram program;
	private final int stackSize;
	private final int memSize;
	private final int initialSP;

	private MicrocodeSimulator simulator;
	private JopTargetState state = JopTargetState.NOT_STARTED;
	private final List<IJopTargetListener> listeners = new CopyOnWriteArrayList<>();

	/** Tracks the reason for the next SUSPENDED event. */
	private volatile JopSuspendReason pendingSuspendReason = JopSuspendReason.UNKNOWN;

	/** Tracks the breakpoint slot for the next BREAKPOINT event. */
	private volatile int pendingBreakpointSlot = -1;

	/** Breakpoint slot management: slot → BreakpointEntry. */
	private final Map<Integer, BreakpointEntry> breakpointSlots = new ConcurrentHashMap<>();
	private int nextSlot = 0;

	private record BreakpointEntry(JopBreakpointType type, int address, int sourceLine) {}

	public SimulatorJopTarget(MicrocodeProgram program, int stackSize, int memSize, int initialSP) {
		this.program = program;
		this.stackSize = stackSize;
		this.memSize = memSize;
		this.initialSP = initialSP;
	}

	@Override
	public String getName() {
		return "JOP Simulator";
	}

	@Override
	public String getTargetTypeId() {
		return "simulator";
	}

	@Override
	public void connect() throws JopTargetException {
		simulator = new MicrocodeSimulator(stackSize, 256, memSize);
		simulator.load(program);
		simulator.setSP(initialSP);
		state = JopTargetState.NOT_STARTED;
		breakpointSlots.clear();
		nextSlot = 0;

		simulator.addListener(new ISimulatorListener() {
			@Override
			public void stateChanged(SimulatorState newState) {
				JopTargetState mapped = mapState(newState);
				state = mapped;
				JopSuspendReason reason = null;
				int slot = -1;
				if (mapped == JopTargetState.SUSPENDED) {
					reason = pendingSuspendReason;
					if (reason == JopSuspendReason.BREAKPOINT) {
						slot = pendingBreakpointSlot;
						pendingBreakpointSlot = -1;
					}
					pendingSuspendReason = JopSuspendReason.UNKNOWN;
				}
				fireStateChanged(mapped, reason, slot);
			}

			@Override
			public void outputProduced(String text) {
				fireOutputProduced(text);
			}
		});
	}

	@Override
	public void disconnect() throws JopTargetException {
		if (simulator != null && state != JopTargetState.TERMINATED) {
			simulator.terminate();
		}
	}

	@Override
	public void resume() throws JopTargetException {
		checkConnected();
		pendingSuspendReason = JopSuspendReason.BREAKPOINT;
		pendingBreakpointSlot = findHitBreakpointSlot();
		simulator.resume();
	}

	@Override
	public void suspend() throws JopTargetException {
		checkConnected();
		pendingSuspendReason = JopSuspendReason.MANUAL;
		simulator.suspend();
	}

	@Override
	public void terminate() throws JopTargetException {
		checkConnected();
		simulator.terminate();
	}

	@Override
	public void reset() throws JopTargetException {
		checkConnected();
		simulator.load(program);
		simulator.setSP(initialSP);
		state = JopTargetState.SUSPENDED;
		fireStateChanged(JopTargetState.SUSPENDED, JopSuspendReason.RESET, -1);
	}

	@Override
	public void stepMicro() throws JopTargetException {
		checkConnected();
		pendingSuspendReason = JopSuspendReason.STEP_COMPLETE;
		simulator.stepOver();
	}

	@Override
	public void stepBytecode() throws JopTargetException {
		checkConnected();
		pendingSuspendReason = JopSuspendReason.STEP_COMPLETE;
		int startJpc = simulator.getJPC();
		int maxSteps = 10000;
		int steps = 0;
		// Step until jpc changes, termination, step limit, or interruption
		do {
			simulator.stepOver();
			steps++;
			if (simulator.getState() == SimulatorState.TERMINATED
					|| steps >= maxSteps
					|| Thread.currentThread().isInterrupted()) {
				break;
			}
		} while (simulator.getJPC() == startJpc);
	}

	@Override
	public JopTargetState getState() {
		return state;
	}

	@Override
	public JopRegisters readRegisters() throws JopTargetException {
		checkConnected();
		return new JopRegisters(
				simulator.getA(),
				simulator.getB(),
				simulator.getPC(),
				simulator.getSP(),
				simulator.getVP(),
				simulator.getAR(),
				simulator.getJPC(),
				0, // mulResult not directly exposed by simulator
				0, 0, 0, simulator.getMemReadData(),
				0, 0, 0); // extended registers not available in simulator
	}

	@Override
	public JopStackData readStack() throws JopTargetException {
		checkConnected();
		int sp = simulator.getSP();
		int count = Math.min(sp + 1, 32);
		int[] values = new int[count];
		for (int i = 0; i < count; i++) {
			values[i] = simulator.getStackValue(i);
		}
		return new JopStackData(values, sp);
	}

	@Override
	public JopMemoryData readMemory(int address, int length) throws JopTargetException {
		checkConnected();
		int[] values = new int[length];
		for (int i = 0; i < length; i++) {
			values[i] = simulator.getMemValue(address + i);
		}
		return new JopMemoryData(address, values);
	}

	@Override
	public void writeRegister(JopRegister reg, int value) throws JopTargetException {
		checkConnected();
		switch (reg) {
			case A -> simulator.setA(value);
			case B -> simulator.setB(value);
			case SP -> simulator.setSP(value);
			case VP -> simulator.setVP(value);
			case PC -> simulator.setPC(value);
			default -> throw new JopTargetException("Register not writable: " + reg.displayName());
		}
	}

	@Override
	public void writeMemory(int address, int value) throws JopTargetException {
		checkConnected();
		simulator.setMemValue(address, value);
	}

	@Override
	public int setBreakpoint(JopBreakpointType type, int address) throws JopTargetException {
		checkConnected();
		if (type == JopBreakpointType.BYTECODE_JPC) {
			throw new JopTargetException("Simulator does not support JPC breakpoints");
		}
		// Convert address to source line for the simulator
		Integer sourceLine = program.statementToLine().get(address);
		if (sourceLine == null) {
			throw new JopTargetException("No source line for address " + address);
		}
		simulator.addBreakpoint(sourceLine);
		int slot = nextSlot++;
		breakpointSlots.put(slot, new BreakpointEntry(type, address, sourceLine));
		return slot;
	}

	@Override
	public void clearBreakpoint(int slot) throws JopTargetException {
		checkConnected();
		BreakpointEntry entry = breakpointSlots.remove(slot);
		if (entry != null) {
			simulator.removeBreakpoint(entry.sourceLine());
		}
	}

	@Override
	public JopBreakpointInfo[] getBreakpoints() {
		return breakpointSlots.entrySet().stream()
				.map(e -> new JopBreakpointInfo(e.getKey(), e.getValue().type(), e.getValue().address()))
				.toArray(JopBreakpointInfo[]::new);
	}

	@Override
	public JopTargetInfo getTargetInfo() {
		return new JopTargetInfo(1, Integer.MAX_VALUE, stackSize, memSize, "simulator", 1, 0, 0);
	}

	@Override
	public int resolveLineToAddress(int sourceLine) {
		Integer addr = program.lineToStatement().get(sourceLine);
		return addr != null ? addr : -1;
	}

	@Override
	public void provideInput(String text) throws JopTargetException {
		checkConnected();
		simulator.provideInput(text);
	}

	@Override
	public int getCurrentSourceLine() {
		if (simulator == null) return -1;
		return simulator.getCurrentSourceLine();
	}

	@Override
	public String getCurrentInstructionName() {
		if (simulator == null) return null;
		MicrocodeStatement stmt = simulator.getCurrentStatement();
		return stmt != null ? stmt.mnemonic() : null;
	}

	@Override
	public void addListener(IJopTargetListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(IJopTargetListener listener) {
		listeners.remove(listener);
	}

	/** Get the underlying simulator (for debug model access). */
	public MicrocodeSimulator getSimulator() {
		return simulator;
	}

	private void checkConnected() throws JopTargetException {
		if (simulator == null) {
			throw new JopTargetException("Target not connected");
		}
	}

	private JopTargetState mapState(SimulatorState simState) {
		return switch (simState) {
			case NOT_STARTED -> JopTargetState.NOT_STARTED;
			case RUNNING -> JopTargetState.RUNNING;
			case SUSPENDED -> JopTargetState.SUSPENDED;
			case TERMINATED -> JopTargetState.TERMINATED;
		};
	}

	private void fireStateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
		for (IJopTargetListener l : listeners) {
			l.stateChanged(newState, reason, breakpointSlot);
		}
	}

	/**
	 * Find the breakpoint slot that matches the current PC (source line).
	 * Returns -1 if no breakpoint matches.
	 */
	private int findHitBreakpointSlot() {
		if (simulator == null) return -1;
		int currentLine = simulator.getCurrentSourceLine();
		for (var entry : breakpointSlots.entrySet()) {
			if (entry.getValue().sourceLine() == currentLine) {
				return entry.getKey();
			}
		}
		return -1;
	}

	private void fireOutputProduced(String text) {
		for (IJopTargetListener l : listeners) {
			l.outputProduced(text);
		}
	}
}
