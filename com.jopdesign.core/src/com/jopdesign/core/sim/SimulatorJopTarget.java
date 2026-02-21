package com.jopdesign.core.sim;

import java.util.List;
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

		simulator.addListener(new ISimulatorListener() {
			@Override
			public void stateChanged(SimulatorState newState) {
				JopTargetState mapped = mapState(newState);
				state = mapped;
				fireStateChanged(mapped);
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
		simulator.resume();
	}

	@Override
	public void suspend() throws JopTargetException {
		checkConnected();
		simulator.suspend();
	}

	@Override
	public void terminate() throws JopTargetException {
		checkConnected();
		simulator.terminate();
	}

	@Override
	public void stepMicro() throws JopTargetException {
		checkConnected();
		simulator.stepOver();
	}

	@Override
	public void stepBytecode() throws JopTargetException {
		checkConnected();
		int startJpc = simulator.getJPC();
		int maxSteps = 10000;
		int steps = 0;
		// Step until jpc changes, termination, or step limit
		do {
			simulator.stepOver();
			steps++;
			if (simulator.getState() == SimulatorState.TERMINATED || steps >= maxSteps) {
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
				0, 0, 0L, // mulA, mulB, mulResult not directly exposed by simulator
				0, 0, 0, simulator.getMemReadData());
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
	public void writeRegister(String name, int value) throws JopTargetException {
		checkConnected();
		switch (name) {
			case "a" -> simulator.setA(value);
			case "b" -> simulator.setB(value);
			case "sp" -> simulator.setSP(value);
			case "vp" -> simulator.setVP(value);
			case "pc" -> simulator.setPC(value);
			default -> throw new JopTargetException("Unknown register: " + name);
		}
	}

	@Override
	public void writeMemory(int address, int value) throws JopTargetException {
		checkConnected();
		simulator.setMemValue(address, value);
	}

	@Override
	public void addBreakpoint(int sourceLine) throws JopTargetException {
		checkConnected();
		simulator.addBreakpoint(sourceLine);
	}

	@Override
	public void removeBreakpoint(int sourceLine) throws JopTargetException {
		checkConnected();
		simulator.removeBreakpoint(sourceLine);
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

	private void fireStateChanged(JopTargetState newState) {
		for (IJopTargetListener l : listeners) {
			l.stateChanged(newState);
		}
	}

	private void fireOutputProduced(String text) {
		for (IJopTargetListener l : listeners) {
			l.outputProduced(text);
		}
	}
}
