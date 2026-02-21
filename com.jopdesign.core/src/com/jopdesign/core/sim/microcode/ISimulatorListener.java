package com.jopdesign.core.sim.microcode;

/**
 * Listener for microcode simulator state changes and output.
 */
public interface ISimulatorListener {

	/** Called when the simulator state changes (e.g. suspended, terminated). */
	void stateChanged(SimulatorState newState);

	/** Called when the simulator produces output (e.g. UART TX). */
	void outputProduced(String text);
}
