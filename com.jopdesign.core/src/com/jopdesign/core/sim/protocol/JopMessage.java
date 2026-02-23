package com.jopdesign.core.sim.protocol;

import java.util.Arrays;

/**
 * A debug protocol message (request, response, or notification).
 *
 * @param type    message type (see {@link JopMessageType})
 * @param core    core ID (0 for single-core, 0xFF for broadcast)
 * @param payload payload bytes (may be empty, never null)
 */
public record JopMessage(int type, int core, byte[] payload) {

	public JopMessage {
		if (payload == null) {
			payload = new byte[0];
		}
	}

	/** Create a message with no payload. */
	public static JopMessage of(int type, int core) {
		return new JopMessage(type, core, new byte[0]);
	}

	/** Create a message with payload. */
	public static JopMessage of(int type, int core, byte[] payload) {
		return new JopMessage(type, core, payload);
	}

	/** Read a big-endian 16-bit unsigned value from payload at offset. */
	public int getUint16(int offset) {
		return ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
	}

	/** Read a big-endian 32-bit signed value from payload at offset. */
	public int getInt32(int offset) {
		return ((payload[offset] & 0xFF) << 24)
			| ((payload[offset + 1] & 0xFF) << 16)
			| ((payload[offset + 2] & 0xFF) << 8)
			| (payload[offset + 3] & 0xFF);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof JopMessage other)) return false;
		return type == other.type && core == other.core && Arrays.equals(payload, other.payload);
	}

	@Override
	public int hashCode() {
		return 31 * (31 * type + core) + Arrays.hashCode(payload);
	}

	@Override
	public String toString() {
		return "JopMessage[type=0x%02X, core=%d, payload=%d bytes]"
				.formatted(type, core, payload.length);
	}
}
