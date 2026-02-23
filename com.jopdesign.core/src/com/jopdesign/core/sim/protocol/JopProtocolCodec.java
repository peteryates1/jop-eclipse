package com.jopdesign.core.sim.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Encoder/decoder for JOP debug protocol messages.
 *
 * <p>Wire format:
 * <pre>
 * SYNC(0xA5) | TYPE(1) | LEN_HI(1) | LEN_LO(1) | CORE(1) | PAYLOAD(var) | CRC8(1)
 * </pre>
 *
 * <p>CRC-8/MAXIM is computed over TYPE + LEN + CORE + PAYLOAD (everything
 * except SYNC and CRC itself).
 */
public final class JopProtocolCodec {

	private JopProtocolCodec() {}

	/** Encode a JopMessage into wire format bytes. */
	public static byte[] encode(JopMessage msg) {
		byte[] payload = msg.payload();
		int len = payload.length;
		byte[] frame = new byte[6 + len]; // SYNC + TYPE + LEN_HI + LEN_LO + CORE + payload + CRC

		frame[0] = (byte) JopMessageType.SYNC;
		frame[1] = (byte) msg.type();
		frame[2] = (byte) ((len >> 8) & 0xFF);
		frame[3] = (byte) (len & 0xFF);
		frame[4] = (byte) msg.core();
		System.arraycopy(payload, 0, frame, 5, len);

		// CRC over TYPE + LEN + CORE + PAYLOAD (frame[1] through frame[4+len])
		int crc = Crc8Maxim.compute(frame, 1, 4 + len);
		frame[5 + len] = (byte) crc;

		return frame;
	}

	/**
	 * Decode a single message from an input stream.
	 * Scans for SYNC byte, then reads the rest of the frame.
	 *
	 * @param in      the input stream
	 * @param timeout timeout in milliseconds (used for retry loop)
	 * @return the decoded message
	 * @throws IOException            on I/O error
	 * @throws JopProtocolException   on framing/CRC error
	 */
	public static JopMessage decode(InputStream in, long timeout) throws IOException, JopProtocolException {
		long deadline = System.currentTimeMillis() + timeout;

		// Scan for SYNC byte
		while (true) {
			if (System.currentTimeMillis() > deadline) {
				throw new JopProtocolException("Timeout waiting for SYNC byte");
			}
			int b = readByteWithTimeout(in, deadline);
			if (b == JopMessageType.SYNC) {
				break;
			}
			// Skip non-SYNC bytes (noise/stale data)
		}

		// Read header: TYPE + LEN_HI + LEN_LO + CORE
		byte[] header = readBytesWithTimeout(in, 4, deadline);
		int type = header[0] & 0xFF;
		int lenHi = header[1] & 0xFF;
		int lenLo = header[2] & 0xFF;
		int payloadLen = (lenHi << 8) | lenLo;
		int core = header[3] & 0xFF;

		if (payloadLen > 65535) {
			throw new JopProtocolException("Invalid payload length: " + payloadLen);
		}

		// Read payload
		byte[] payload = readBytesWithTimeout(in, payloadLen, deadline);

		// Read CRC
		int receivedCrc = readByteWithTimeout(in, deadline);

		// Verify CRC: compute over TYPE + LEN + CORE + PAYLOAD
		byte[] crcData = new byte[4 + payloadLen];
		System.arraycopy(header, 0, crcData, 0, 4);
		System.arraycopy(payload, 0, crcData, 4, payloadLen);
		int expectedCrc = Crc8Maxim.compute(crcData);

		if (receivedCrc != expectedCrc) {
			throw new JopProtocolException(
					"CRC mismatch: received 0x%02X, expected 0x%02X".formatted(receivedCrc, expectedCrc));
		}

		return new JopMessage(type, core, payload);
	}

	/**
	 * Decode a message from a byte array (useful for testing).
	 *
	 * @param data complete frame including SYNC and CRC
	 * @return the decoded message
	 * @throws JopProtocolException on framing/CRC error
	 */
	public static JopMessage decodeFrame(byte[] data) throws JopProtocolException {
		if (data.length < 6) {
			throw new JopProtocolException("Frame too short: " + data.length + " bytes");
		}
		if ((data[0] & 0xFF) != JopMessageType.SYNC) {
			throw new JopProtocolException("Missing SYNC byte, got 0x%02X".formatted(data[0] & 0xFF));
		}

		int type = data[1] & 0xFF;
		int lenHi = data[2] & 0xFF;
		int lenLo = data[3] & 0xFF;
		int payloadLen = (lenHi << 8) | lenLo;
		int core = data[4] & 0xFF;

		int expectedFrameLen = 6 + payloadLen;
		if (data.length < expectedFrameLen) {
			throw new JopProtocolException("Frame truncated: expected %d bytes, got %d"
					.formatted(expectedFrameLen, data.length));
		}

		byte[] payload = new byte[payloadLen];
		System.arraycopy(data, 5, payload, 0, payloadLen);

		int receivedCrc = data[5 + payloadLen] & 0xFF;
		int expectedCrc = Crc8Maxim.compute(data, 1, 4 + payloadLen);

		if (receivedCrc != expectedCrc) {
			throw new JopProtocolException(
					"CRC mismatch: received 0x%02X, expected 0x%02X".formatted(receivedCrc, expectedCrc));
		}

		return new JopMessage(type, core, payload);
	}

	// --- Payload builders for common requests ---

	/** Build READ_STACK payload: offset (16-bit) + count (16-bit). */
	public static byte[] readStackPayload(int offset, int count) {
		return new byte[] {
			(byte) ((offset >> 8) & 0xFF), (byte) (offset & 0xFF),
			(byte) ((count >> 8) & 0xFF), (byte) (count & 0xFF)
		};
	}

	/** Build READ_MEMORY payload: addr (32-bit) + count (32-bit). */
	public static byte[] readMemoryPayload(int addr, int count) {
		return new byte[] {
			(byte) ((addr >> 24) & 0xFF), (byte) ((addr >> 16) & 0xFF),
			(byte) ((addr >> 8) & 0xFF), (byte) (addr & 0xFF),
			(byte) ((count >> 24) & 0xFF), (byte) ((count >> 16) & 0xFF),
			(byte) ((count >> 8) & 0xFF), (byte) (count & 0xFF)
		};
	}

	/** Build WRITE_REGISTER payload: regId (1) + value (32-bit). */
	public static byte[] writeRegisterPayload(int regId, int value) {
		return new byte[] {
			(byte) regId,
			(byte) ((value >> 24) & 0xFF), (byte) ((value >> 16) & 0xFF),
			(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)
		};
	}

	/** Build WRITE_MEMORY payload: addr (32-bit) + value (32-bit). */
	public static byte[] writeMemoryPayload(int addr, int value) {
		return readMemoryPayload(addr, value); // Same format: two 32-bit BE values
	}

	/** Build WRITE_MEMORY_BLOCK payload: addr (32-bit) + count (32-bit) + data. */
	public static byte[] writeMemoryBlockPayload(int addr, int[] values) {
		byte[] payload = new byte[8 + values.length * 4];
		putInt32(payload, 0, addr);
		putInt32(payload, 4, values.length);
		for (int i = 0; i < values.length; i++) {
			putInt32(payload, 8 + i * 4, values[i]);
		}
		return payload;
	}

	/** Build SET_BREAKPOINT payload: type (1) + addr (32-bit). */
	public static byte[] setBreakpointPayload(int bpType, int address) {
		return new byte[] {
			(byte) bpType,
			(byte) ((address >> 24) & 0xFF), (byte) ((address >> 16) & 0xFF),
			(byte) ((address >> 8) & 0xFF), (byte) (address & 0xFF)
		};
	}

	/** Build CLEAR_BREAKPOINT payload: slot (1). */
	public static byte[] clearBreakpointPayload(int slot) {
		return new byte[] { (byte) slot };
	}

	// --- Payload parsers for common responses ---

	/** Parse REGISTERS payload into an array of 32-bit values. */
	public static int[] parseRegisters(byte[] payload) {
		int count = payload.length / 4;
		int[] regs = new int[count];
		for (int i = 0; i < count; i++) {
			regs[i] = getInt32(payload, i * 4);
		}
		return regs;
	}

	/** Parse STACK_DATA or MEMORY_DATA payload into an array of 32-bit values. */
	public static int[] parseWordArray(byte[] payload) {
		return parseRegisters(payload); // Same format
	}

	/** Parse STATUS payload: [state, reason]. */
	public static int[] parseStatus(byte[] payload) {
		if (payload.length < 2) {
			return new int[] { 0, 0 };
		}
		return new int[] { payload[0] & 0xFF, payload[1] & 0xFF };
	}

	/** Parse HALTED payload: [reason, slot]. */
	public static int[] parseHalted(byte[] payload) {
		return parseStatus(payload); // Same format: reason + slot
	}

	/** Read a big-endian 32-bit int from a byte array. */
	public static int getInt32(byte[] data, int offset) {
		return ((data[offset] & 0xFF) << 24)
			| ((data[offset + 1] & 0xFF) << 16)
			| ((data[offset + 2] & 0xFF) << 8)
			| (data[offset + 3] & 0xFF);
	}

	/** Write a big-endian 32-bit int to a byte array. */
	public static void putInt32(byte[] data, int offset, int value) {
		data[offset] = (byte) ((value >> 24) & 0xFF);
		data[offset + 1] = (byte) ((value >> 16) & 0xFF);
		data[offset + 2] = (byte) ((value >> 8) & 0xFF);
		data[offset + 3] = (byte) (value & 0xFF);
	}

	// --- I/O helpers ---

	private static int readByteWithTimeout(InputStream in, long deadline)
			throws IOException, JopProtocolException {
		while (System.currentTimeMillis() <= deadline) {
			if (in.available() > 0) {
				int b = in.read();
				if (b < 0) {
					throw new JopProtocolException("Unexpected end of stream");
				}
				return b;
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new JopProtocolException("Interrupted while reading");
			}
		}
		throw new JopProtocolException("Timeout reading byte");
	}

	private static byte[] readBytesWithTimeout(InputStream in, int count, long deadline)
			throws IOException, JopProtocolException {
		byte[] buf = new byte[count];
		int pos = 0;
		while (pos < count) {
			if (System.currentTimeMillis() > deadline) {
				throw new JopProtocolException("Timeout reading %d bytes (got %d)".formatted(count, pos));
			}
			int n = in.read(buf, pos, count - pos);
			if (n < 0) {
				throw new JopProtocolException("Unexpected end of stream after %d of %d bytes".formatted(pos, count));
			}
			if (n > 0) {
				pos += n;
			} else {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new JopProtocolException("Interrupted while reading");
				}
			}
		}
		return buf;
	}
}
