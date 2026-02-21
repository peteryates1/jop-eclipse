package com.jopdesign.core.sim;

import java.util.List;
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
		fireStateChanged(state);
	}

	@Override
	public void disconnect() throws JopTargetException {
		state = JopTargetState.TERMINATED;
		fireStateChanged(state);
	}

	@Override
	public void resume() throws JopTargetException {
		checkNotTerminated();
		state = JopTargetState.RUNNING;
		fireStateChanged(state);
		// Immediately hit a "breakpoint"
		cannedPC++;
		cannedSourceLine++;
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state);
	}

	@Override
	public void suspend() throws JopTargetException {
		checkNotTerminated();
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state);
	}

	@Override
	public void terminate() throws JopTargetException {
		state = JopTargetState.TERMINATED;
		fireStateChanged(state);
	}

	@Override
	public void stepMicro() throws JopTargetException {
		checkNotTerminated();
		cannedPC = (cannedPC + 1) % CANNED_MNEMONICS.length;
		cannedA += 3;
		cannedSourceLine++;
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state);
	}

	@Override
	public void stepBytecode() throws JopTargetException {
		checkNotTerminated();
		cannedPC = (cannedPC + 2) % CANNED_MNEMONICS.length;
		cannedJPC++;
		cannedA += 5;
		cannedSourceLine += 2;
		state = JopTargetState.SUSPENDED;
		fireStateChanged(state);
	}

	@Override
	public JopTargetState getState() {
		return state;
	}

	@Override
	public JopRegisters readRegisters() throws JopTargetException {
		return new JopRegisters(
				cannedA, cannedB, cannedPC, cannedSP, cannedVP, cannedAR, cannedJPC,
				0, 0, 0L,
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
	public void writeRegister(String name, int value) throws JopTargetException {
		switch (name) {
			case "a" -> cannedA = value;
			case "b" -> cannedB = value;
			case "sp" -> cannedSP = value;
			case "vp" -> cannedVP = value;
			case "pc" -> cannedPC = value;
			default -> throw new JopTargetException("Unknown register: " + name);
		}
	}

	@Override
	public void writeMemory(int address, int value) throws JopTargetException {
		// No-op for dummy
	}

	@Override
	public void addBreakpoint(int sourceLine) throws JopTargetException {
		// No-op for dummy
	}

	@Override
	public void removeBreakpoint(int sourceLine) throws JopTargetException {
		// No-op for dummy
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

	private void fireStateChanged(JopTargetState newState) {
		for (IJopTargetListener l : listeners) {
			l.stateChanged(newState);
		}
	}
}
