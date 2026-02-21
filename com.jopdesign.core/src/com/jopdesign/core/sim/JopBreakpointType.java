package com.jopdesign.core.sim;

/**
 * Type of breakpoint, matching the debug protocol breakpoint type codes.
 */
public enum JopBreakpointType {
	MICRO_PC(0x00),
	BYTECODE_JPC(0x01);

	private final int protocolCode;

	JopBreakpointType(int protocolCode) {
		this.protocolCode = protocolCode;
	}

	public int protocolCode() {
		return protocolCode;
	}
}
