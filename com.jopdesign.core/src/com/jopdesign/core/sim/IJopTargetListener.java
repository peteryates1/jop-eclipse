package com.jopdesign.core.sim;

/**
 * Listener for JOP target state changes and output.
 */
public interface IJopTargetListener {

	/**
	 * Called when the target's execution state changes.
	 *
	 * @param newState       the new state
	 * @param reason         the suspend reason when newState is SUSPENDED; null otherwise
	 * @param breakpointSlot the breakpoint slot that was hit when reason is BREAKPOINT;
	 *                       -1 otherwise (maps to 0xFF on wire)
	 */
	void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot);

	/**
	 * Called when the target produces text output (e.g. UART TX).
	 *
	 * @param text the output text
	 */
	void outputProduced(String text);
}
