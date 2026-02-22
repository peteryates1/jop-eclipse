package com.jopdesign.core.sim;

/**
 * Snapshot of JOP processor registers, aligned with the debug protocol's
 * register set.
 *
 * @param a            TOS register (top of stack)
 * @param b            NOS register (next of stack)
 * @param pc           microcode program counter
 * @param sp           stack pointer
 * @param vp           variable pointer (frame pointer)
 * @param ar           address register
 * @param jpc          Java program counter
 * @param mulResult    multiply unit result (lower 32 bits)
 * @param memReadAddr  memory read address register
 * @param memWriteAddr memory write address register
 * @param memWriteData memory write data register
 * @param memReadData  memory read data register
 * @param flags        processor flags (extended)
 * @param instr        current instruction (extended)
 * @param jopd         debug data register (extended)
 */
public record JopRegisters(
		int a,
		int b,
		int pc,
		int sp,
		int vp,
		int ar,
		int jpc,
		int mulResult,
		int memReadAddr,
		int memWriteAddr,
		int memWriteData,
		int memReadData,
		int flags,
		int instr,
		int jopd) {
}
