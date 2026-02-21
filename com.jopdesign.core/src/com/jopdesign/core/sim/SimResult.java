package com.jopdesign.core.sim;

/**
 * Result of a JopSim simulation run.
 *
 * @param success      whether the simulation completed without errors
 * @param instructions total bytecode instructions executed
 * @param cycles       total simulated clock cycles
 * @param cpi          cycles per instruction
 * @param summary      human-readable statistics summary (memory stats, cache info, etc.)
 */
public record SimResult(
		boolean success,
		long instructions,
		long cycles,
		double cpi,
		String summary
) {
	/**
	 * Format a concise one-line summary of the simulation result.
	 */
	public String oneLine() {
		if (instructions == 0) {
			return success ? "Simulation completed (no statistics)" : "Simulation failed";
		}
		return String.format("%,d instructions, %,d cycles, CPI=%.1f",
				instructions, cycles, cpi);
	}
}
