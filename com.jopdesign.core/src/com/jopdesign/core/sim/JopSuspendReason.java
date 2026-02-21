package com.jopdesign.core.sim;

/**
 * Reason why a JOP target entered the SUSPENDED state.
 * Matches the protocol STATUS reason codes (0x00-0x04).
 */
public enum JopSuspendReason {
	MANUAL(0x00),
	BREAKPOINT(0x01),
	STEP_COMPLETE(0x02),
	RESET(0x03),
	FAULT(0x04),
	UNKNOWN(-1);

	private final int protocolCode;

	JopSuspendReason(int protocolCode) {
		this.protocolCode = protocolCode;
	}

	public int protocolCode() {
		return protocolCode;
	}

	/**
	 * Look up a suspend reason by its protocol code.
	 *
	 * @param code the protocol code
	 * @return the reason, or {@link #UNKNOWN} if not recognized
	 */
	public static JopSuspendReason byProtocolCode(int code) {
		for (JopSuspendReason reason : values()) {
			if (reason.protocolCode == code) {
				return reason;
			}
		}
		return UNKNOWN;
	}
}
