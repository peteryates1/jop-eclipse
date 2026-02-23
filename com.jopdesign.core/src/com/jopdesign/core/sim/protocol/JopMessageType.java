package com.jopdesign.core.sim.protocol;

/**
 * Debug protocol message types as defined in jop-debug-protocol.md.
 */
public final class JopMessageType {

	private JopMessageType() {}

	// Host -> Target (Requests)
	public static final int HALT             = 0x01;
	public static final int RESUME           = 0x02;
	public static final int STEP_MICRO       = 0x03;
	public static final int STEP_BYTECODE    = 0x04;
	public static final int RESET            = 0x05;
	public static final int QUERY_STATUS     = 0x06;
	public static final int READ_REGISTERS   = 0x10;
	public static final int READ_STACK       = 0x11;
	public static final int READ_MEMORY      = 0x12;
	public static final int WRITE_REGISTER   = 0x13;
	public static final int WRITE_MEMORY     = 0x14;
	public static final int WRITE_MEMORY_BLOCK = 0x15;
	public static final int SET_BREAKPOINT   = 0x20;
	public static final int CLEAR_BREAKPOINT = 0x21;
	public static final int QUERY_BREAKPOINTS = 0x22;
	public static final int PING             = 0xF0;
	public static final int QUERY_INFO       = 0xF1;

	// Target -> Host (Responses)
	public static final int ACK              = 0x80;
	public static final int NAK              = 0x81;
	public static final int REGISTERS        = 0x82;
	public static final int STACK_DATA       = 0x83;
	public static final int MEMORY_DATA      = 0x84;
	public static final int STATUS           = 0x85;
	public static final int BREAKPOINT_LIST  = 0x86;
	public static final int TARGET_INFO      = 0x87;
	public static final int PONG             = 0x88;

	// Target -> Host (Async Notification)
	public static final int HALTED           = 0xC0;

	/** SYNC byte for frame synchronization. */
	public static final int SYNC = 0xA5;

	/** Broadcast core ID (all cores). */
	public static final int CORE_BROADCAST = 0xFF;

	/** Check if a message type is an async notification. */
	public static boolean isNotification(int type) {
		return type == HALTED;
	}

	/** Check if a message type is a response (0x80-0x8F). */
	public static boolean isResponse(int type) {
		return type >= 0x80 && type <= 0x8F;
	}

	/** Check if a message type is a request (from host). */
	public static boolean isRequest(int type) {
		return !isResponse(type) && !isNotification(type);
	}
}
