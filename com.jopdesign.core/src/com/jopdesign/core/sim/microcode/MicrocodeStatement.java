package com.jopdesign.core.sim.microcode;

/**
 * A single parsed microcode instruction.
 *
 * @param mnemonic        the instruction mnemonic (e.g. "add", "bz", "ldm")
 * @param operandText     the raw operand text from source (may be null)
 * @param resolvedOperand the numeric value of the operand after resolving labels/constants/variables
 * @param sourceLine      1-based line number in the source file
 */
public record MicrocodeStatement(
		String mnemonic,
		String operandText,
		int resolvedOperand,
		int sourceLine
) {
}
