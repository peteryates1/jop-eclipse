package com.jopdesign.core.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dummy IJopTarget that returns canned data for UI development and testing.
 * No real execution occurs; state transitions are simulated.
 */
public class DummyJopTarget implements IJopTarget {

	private JopTargetState state = JopTargetState.NOT_STARTED;
	private final List<IJopTargetListener> listeners = new CopyOnWriteArrayList<>();

	private int cannedPC;
	private int cannedSP = 4;
	private int cannedA = 42;
	private int cannedB = 7;
	private int cannedVP = 0;
	private int cannedAR = 0;
	private int cannedJPC = 0;
	private int cannedSourceLine = 1;

	private static final int INITIAL_PC = 0;
	private static final int INITIAL_SP = 4;
	private static final int INITIAL_A = 42;
	private static final int INITIAL_B = 7;

	/** Breakpoint slot management. */
	private final Map<Integer, JopBreakpointInfo> breakpointSlots = new HashMap<>();
	private int nextSlot = 0;

	private static final int[] CANNED_STACK = { 100, 200, 300, 400, 500, 600, 700, 800 };
	private static final String[] CANNED_MNEMONICS = {
		"nop", "dup", "add", "pop", "ld0", "st0", "ldi", "jmp"
	};

	@Override
	public String getName() {
		return "JOP Dummy Target";
	}

	@Override
	public String getTargetTypeId() {
		return "dummy";
	}

	@Override
	public void connect() throws JopTargetException {
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state, JopSuspendReason.MANUAL);
	}

	@Override
	public void disconnect() throws JopTargetException {
		state = JopTargetState.TERMINATED;
		fireStateChanged(state, null);
	}

	@Override
	public void resume() throws JopTargetException {
		checkNotTerminated();
		state = JopTargetState.RUNNING;
		fireStateChanged(state, null);
		// Immediately hit a "breakpoint"
		cannedPC++;
		cannedSourceLine++;
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state, JopSuspendReason.BREAKPOINT);
	}

	@Override
	public void suspend() throws JopTargetException {
		checkNotTerminated();
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state, JopSuspendReason.MANUAL);
	}

	@Override
	public void terminate() throws JopTargetException {
		state = JopTargetState.TERMINATED;
		fireStateChanged(state, null);
	}

	@Override
	public void reset() throws JopTargetException {
		cannedPC = INITIAL_PC;
		cannedSP = INITIAL_SP;
		cannedA = INITIAL_A;
		cannedB = INITIAL_B;
		cannedVP = 0;
		cannedAR = 0;
		cannedJPC = 0;
		cannedSourceLine = 1;
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state, JopSuspendReason.RESET);
	}

	@Override
	public void stepMicro() throws JopTargetException {
		checkNotTerminated();
		cannedPC = (cannedPC + 1) % CANNED_MNEMONICS.length;
		cannedA += 3;
		cannedSourceLine++;
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state, JopSuspendReason.STEP_COMPLETE);
	}

	@Override
	public void stepBytecode() throws JopTargetException {
		checkNotTerminated();
		cannedPC = (cannedPC + 2) % CANNED_MNEMONICS.length;
		cannedJPC++;
		cannedA += 5;
		cannedSourceLine += 2;
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state, JopSuspendReason.STEP_COMPLETE);
	}

	@Override
	public JopTargetState getState() {
		return state;
	}

	@Override
	public JopRegisters readRegisters() throws JopTargetException {
		return new JopRegisters(
				cannedA, cannedB, cannedPC, cannedSP, cannedVP, cannedAR, cannedJPC,
				0,
				0, 0, 0, 0);
	}

	@Override
	public JopStackData readStack() throws JopTargetException {
		int count = Math.min(cannedSP + 1, CANNED_STACK.length);
		int[] values = new int[count];
		System.arraycopy(CANNED_STACK, 0, values, 0, count);
		return new JopStackData(values, cannedSP);
	}

	@Override
	public JopMemoryData readMemory(int address, int length) throws JopTargetException {
		int[] values = new int[length];
		for (int i = 0; i < length; i++) {
			values[i] = (address + i) * 4; // Canned pattern
		}
		return new JopMemoryData(address, values);
	}

	@Override
	public void writeRegister(JopRegister reg, int value) throws JopTargetException {
		switch (reg) {
			case A -> cannedA = value;
			case B -> cannedB = value;
			case SP -> cannedSP = value;
			case VP -> cannedVP = value;
			case PC -> cannedPC = value;
			default -> throw new JopTargetException("Register not writable: " + reg.displayName());
		}
	}

	@Override
	public void writeMemory(int address, int value) throws JopTargetException {
		// No-op for dummy
	}

	@Override
	public int setBreakpoint(JopBreakpointType type, int address) throws JopTargetException {
		int slot = nextSlot++;
		breakpointSlots.put(slot, new JopBreakpointInfo(slot, type, address));
		return slot;
	}

	@Override
	public void clearBreakpoint(int slot) throws JopTargetException {
		breakpointSlots.remove(slot);
	}

	@Override
	public JopBreakpointInfo[] getBreakpoints() {
		return breakpointSlots.values().toArray(new JopBreakpointInfo[0]);
	}

	@Override
	public JopTargetInfo getTargetInfo() {
		return new JopTargetInfo(1, 4, 128, 1024, "dummy");
	}

	@Override
	public int resolveLineToAddress(int sourceLine) {
		return sourceLine; // Identity for dummy
	}

	@Override
	public void provideInput(String text) throws JopTargetException {
		// No-op for dummy
	}

	@Override
	public int getCurrentSourceLine() {
		return cannedSourceLine;
	}

	@Override
	public String getCurrentInstructionName() {
		return CANNED_MNEMONICS[cannedPC % CANNED_MNEMONICS.length];
	}

	@Override
	public void addListener(IJopTargetListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(IJopTargetListener listener) {
		listeners.remove(listener);
	}

	private void checkNotTerminated() throws JopTargetException {
		if (state == JopTargetState.TERMINATED) {
			throw new JopTargetException("Target is terminated");
		}
	}

	private void fireStateChanged(JopTargetState newState, JopSuspendReason reason) {
		for (IJopTargetListener l : listeners) {
			l.stateChanged(newState, reason);
		}
	}
}
