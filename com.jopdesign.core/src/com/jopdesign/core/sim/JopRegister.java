package com.jopdesign.core.sim;

/**
 * JOP processor registers as defined by the debug protocol.
 * Each register has a protocol ID (wire format) and a display name.
 */
public enum JopRegister {
	PC(0x00, "pc"),
	JPC(0x01, "jpc"),
	A(0x02, "A (TOS)"),
	B(0x03, "B (NOS)"),
	SP(0x04, "sp"),
	VP(0x05, "vp"),
	AR(0x06, "ar"),
	MUL_RESULT(0x07, "mulResult"),
	MEM_RD_ADDR(0x08, "memReadAddr"),
	MEM_WR_ADDR(0x09, "memWriteAddr"),
	MEM_RD_DATA(0x0A, "memReadData"),
	MEM_WR_DATA(0x0B, "memWriteData"),
	FLAGS(0x0C, "flags"),
	INSTR(0x0D, "instr"),
	JOPD(0x0E, "jopd");

	private final int protocolId;
	private final String displayName;

	JopRegister(int protocolId, String displayName) {
		this.protocolId = protocolId;
		this.displayName = displayName;
	}

	public int protocolId() {
		return protocolId;
	}

	public String displayName() {
		return displayName;
	}

	/** Returns true if this is an extended register (FLAGS, INSTR, JOPD). */
	public boolean isExtended() {
		return protocolId >= 0x0C && protocolId <= 0x0E;
	}

	/**
	 * Look up a register by its protocol ID.
	 *
	 * @param id the protocol ID (0x00-0x0E)
	 * @return the register
	 * @throws IllegalArgumentException if no register has that ID
	 */
	public static JopRegister byProtocolId(int id) {
		for (JopRegister reg : values()) {
			if (reg.protocolId == id) {
				return reg;
			}
		}
		throw new IllegalArgumentException("Unknown register protocol ID: 0x" + Integer.toHexString(id));
	}
}
