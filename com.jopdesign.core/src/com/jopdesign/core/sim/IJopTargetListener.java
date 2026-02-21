package com.jopdesign.core.sim;

/**
 * Listener for JOP target state changes and output.
 */
public interface IJopTargetListener {

	/**
	 * Called when the target's execution state changes.
	 *
	 * @param newState the new state
	 * @param reason   the suspend reason when newState is SUSPENDED; null otherwise
	 */
	void stateChanged(JopTargetState newState, JopSuspendReason reason);

	/**
	 * Called when the target produces text output (e.g. UART TX).
	 *
	 * @param text the output text
	 */
	void outputProduced(String text);
}
