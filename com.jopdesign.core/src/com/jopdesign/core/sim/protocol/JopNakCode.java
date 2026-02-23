package com.jopdesign.core.sim.protocol;

/**
 * NAK error codes as defined in the debug protocol.
 */
public enum JopNakCode {
	CPU_NOT_HALTED(0x01, "CPU not halted"),
	NO_FREE_BREAKPOINT_SLOTS(0x02, "No free breakpoint slots"),
	INVALID_REGISTER_ID(0x03, "Invalid register ID"),
	INVALID_MEMORY_ADDRESS(0x04, "Invalid memory address"),
	INVALID_BREAKPOINT_SLOT(0x05, "Invalid breakpoint slot"),
	UNKNOWN(0xFF, "Unknown/internal error");

	private final int code;
	private final String description;

	JopNakCode(int code, String description) {
		this.code = code;
		this.description = description;
	}

	public int code() {
		return code;
	}

	public String description() {
		return description;
	}

	public static JopNakCode byCode(int code) {
		for (JopNakCode nak : values()) {
			if (nak.code == code) {
				return nak;
			}
		}
		return UNKNOWN;
	}
}
