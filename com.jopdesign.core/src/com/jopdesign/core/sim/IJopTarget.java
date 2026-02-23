package com.jopdesign.core.sim;

/**
 * Abstraction for a JOP execution target. Implementations include the
 * microcode simulator, RTL simulation, FPGA hardware, and a dummy target
 * for UI testing.
 */
public interface IJopTarget {

	/** Human-readable name for this target instance. */
	String getName();

	/** Identifier for the target type (e.g. "simulator", "dummy", "rtlsim", "fpga"). */
	String getTargetTypeId();

	/** Connect to / initialize the target. */
	void connect() throws JopTargetException;

	/** Disconnect from / shut down the target. */
	void disconnect() throws JopTargetException;

	/** Resume execution until breakpoint or termination. */
	void resume() throws JopTargetException;

	/** Suspend execution. */
	void suspend() throws JopTargetException;

	/** Terminate execution. */
	void terminate() throws JopTargetException;

	/** Reset the target to initial state. Fires SUSPENDED with reason RESET. */
	void reset() throws JopTargetException;

	/** Execute one microcode instruction. */
	void stepMicro() throws JopTargetException;

	/** Execute microcode instructions until the JPC changes (one bytecode step). */
	void stepBytecode() throws JopTargetException;

	/** Get the current execution state. */
	JopTargetState getState();

	/** Read all processor registers. */
	JopRegisters readRegisters() throws JopTargetException;

	/** Read the stack contents. */
	JopStackData readStack() throws JopTargetException;

	/** Read a region of memory. */
	JopMemoryData readMemory(int address, int length) throws JopTargetException;

	/** Write a value to a register. */
	void writeRegister(JopRegister reg, int value) throws JopTargetException;

	/** Write a value to a memory address. */
	void writeMemory(int address, int value) throws JopTargetException;

	/**
	 * Write a block of values to consecutive memory addresses.
	 * The default implementation loops over single writes.
	 * Hardware targets can override for efficiency. Max 256 words per spec.
	 *
	 * @param address start address
	 * @param values  values to write
	 */
	default void writeMemoryBlock(int address, int[] values) throws JopTargetException {
		if (values.length > 256) {
			throw new JopTargetException("writeMemoryBlock: max 256 words per call, got " + values.length);
		}
		for (int i = 0; i < values.length; i++) {
			writeMemory(address + i, values[i]);
		}
	}

	/**
	 * Set a breakpoint at the given address.
	 *
	 * @param type breakpoint type
	 * @param address the address to break at
	 * @return the assigned breakpoint slot index
	 */
	int setBreakpoint(JopBreakpointType type, int address) throws JopTargetException;

	/**
	 * Clear a breakpoint by slot index.
	 *
	 * @param slot the slot returned by {@link #setBreakpoint}
	 */
	void clearBreakpoint(int slot) throws JopTargetException;

	/** Query all active breakpoints. */
	JopBreakpointInfo[] getBreakpoints();

	/** Query target capabilities and configuration. */
	JopTargetInfo getTargetInfo();

	/**
	 * Resolve a 1-based source line number to a microcode address (0-based index).
	 *
	 * @param sourceLine 1-based source line
	 * @return 0-based instruction address, or -1 if not found
	 */
	int resolveLineToAddress(int sourceLine);

	/** Provide text input to the target (e.g. UART RX). */
	void provideInput(String text) throws JopTargetException;

	/** Get the 1-based source line number for the current PC, or -1 if unknown. */
	int getCurrentSourceLine();

	/** Get the mnemonic of the current instruction, or null if unknown. */
	String getCurrentInstructionName();

	/** Add a listener for state changes and output. */
	void addListener(IJopTargetListener listener);

	/** Remove a listener. */
	void removeListener(IJopTargetListener listener);
}
