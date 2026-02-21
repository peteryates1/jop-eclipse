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

	/** Write a value to a named register. */
	void writeRegister(String name, int value) throws JopTargetException;

	/** Write a value to a memory address. */
	void writeMemory(int address, int value) throws JopTargetException;

	/** Add a breakpoint at the given source line. */
	void addBreakpoint(int sourceLine) throws JopTargetException;

	/** Remove a breakpoint at the given source line. */
	void removeBreakpoint(int sourceLine) throws JopTargetException;

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
