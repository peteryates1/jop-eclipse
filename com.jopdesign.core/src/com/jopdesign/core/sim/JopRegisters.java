package com.jopdesign.core.sim;

/**
 * Snapshot of JOP processor registers.
 *
 * @param a           TOS register (top of stack)
 * @param b           NOS register (next of stack)
 * @param pc          microcode program counter
 * @param sp          stack pointer
 * @param vp          variable pointer (frame pointer)
 * @param ar          address register
 * @param jpc         Java program counter
 * @param mulA        multiply unit operand A
 * @param mulB        multiply unit operand B
 * @param mulResult   multiply unit result (64-bit)
 * @param memReadAddr memory read address register
 * @param memWriteAddr memory write address register
 * @param memWriteData memory write data register
 * @param memReadData memory read data register
 */
public record JopRegisters(
		int a,
		int b,
		int pc,
		int sp,
		int vp,
		int ar,
		int jpc,
		int mulA,
		int mulB,
		long mulResult,
		int memReadAddr,
		int memWriteAddr,
		int memWriteData,
		int memReadData) {
}
