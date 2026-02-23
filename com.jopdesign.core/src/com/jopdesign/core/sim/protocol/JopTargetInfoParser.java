package com.jopdesign.core.sim.protocol;

import java.nio.charset.StandardCharsets;

import com.jopdesign.core.sim.JopTargetInfo;

/**
 * Parses TARGET_INFO (0x87) response payload containing tag-value pairs.
 *
 * <p>Tag format: TAG(1) + LEN(1) + VALUE(LEN).
 *
 * <p>Tags:
 * <ul>
 *   <li>0x01: NUM_CORES (1 byte)</li>
 *   <li>0x02: NUM_BREAKPOINTS (1 byte)</li>
 *   <li>0x03: STACK_DEPTH (2 bytes BE)</li>
 *   <li>0x04: MEMORY_SIZE (4 bytes BE)</li>
 *   <li>0x05: MICRO_PIPELINE_DEPTH (1 byte)</li>
 *   <li>0x06: BYTECODE_PIPELINE_DEPTH (1 byte)</li>
 *   <li>0x07: VERSION (variable, UTF-8)</li>
 *   <li>0x08: PROTOCOL_VERSION (2 bytes: major + minor)</li>
 *   <li>0x09: EXTENDED_REGISTERS (1 byte bitmask)</li>
 * </ul>
 */
public final class JopTargetInfoParser {

	private JopTargetInfoParser() {}

	public static final int TAG_NUM_CORES = 0x01;
	public static final int TAG_NUM_BREAKPOINTS = 0x02;
	public static final int TAG_STACK_DEPTH = 0x03;
	public static final int TAG_MEMORY_SIZE = 0x04;
	public static final int TAG_MICRO_PIPELINE_DEPTH = 0x05;
	public static final int TAG_BYTECODE_PIPELINE_DEPTH = 0x06;
	public static final int TAG_VERSION = 0x07;
	public static final int TAG_PROTOCOL_VERSION = 0x08;
	public static final int TAG_EXTENDED_REGISTERS = 0x09;

	/**
	 * Parse a TARGET_INFO payload into a JopTargetInfo record.
	 *
	 * @param payload the TARGET_INFO message payload
	 * @return parsed target info with defaults for missing tags
	 */
	public static JopTargetInfo parse(byte[] payload) {
		int numCores = 1;
		int numBreakpoints = 0;
		int stackDepth = 0;
		int memorySize = 0;
		String version = "unknown";
		int protocolMajor = 0;
		int protocolMinor = 0;
		int extendedRegistersMask = 0;

		int pos = 0;
		while (pos + 1 < payload.length) {
			int tag = payload[pos] & 0xFF;
			int len = payload[pos + 1] & 0xFF;
			pos += 2;

			if (pos + len > payload.length) {
				break; // Truncated TLV
			}

			switch (tag) {
				case TAG_NUM_CORES:
					if (len >= 1) numCores = payload[pos] & 0xFF;
					break;
				case TAG_NUM_BREAKPOINTS:
					if (len >= 1) numBreakpoints = payload[pos] & 0xFF;
					break;
				case TAG_STACK_DEPTH:
					if (len >= 2) stackDepth = ((payload[pos] & 0xFF) << 8) | (payload[pos + 1] & 0xFF);
					break;
				case TAG_MEMORY_SIZE:
					if (len >= 4) memorySize = JopProtocolCodec.getInt32(payload, pos);
					break;
				case TAG_VERSION:
					version = new String(payload, pos, len, StandardCharsets.UTF_8);
					break;
				case TAG_PROTOCOL_VERSION:
					if (len >= 2) {
						protocolMajor = payload[pos] & 0xFF;
						protocolMinor = payload[pos + 1] & 0xFF;
					}
					break;
				case TAG_EXTENDED_REGISTERS:
					if (len >= 1) extendedRegistersMask = payload[pos] & 0xFF;
					break;
				// Skip unknown tags silently (forward compatibility)
			}

			pos += len;
		}

		return new JopTargetInfo(numCores, numBreakpoints, stackDepth, memorySize,
				version, protocolMajor, protocolMinor, extendedRegistersMask);
	}

	/**
	 * Encode a JopTargetInfo into a TARGET_INFO payload (for testing).
	 */
	public static byte[] encode(JopTargetInfo info) {
		var buf = new java.io.ByteArrayOutputStream();

		// NUM_CORES
		buf.write(TAG_NUM_CORES);
		buf.write(1);
		buf.write(info.numCores());

		// NUM_BREAKPOINTS
		buf.write(TAG_NUM_BREAKPOINTS);
		buf.write(1);
		buf.write(info.numBreakpoints());

		// STACK_DEPTH
		buf.write(TAG_STACK_DEPTH);
		buf.write(2);
		buf.write((info.stackDepth() >> 8) & 0xFF);
		buf.write(info.stackDepth() & 0xFF);

		// MEMORY_SIZE
		buf.write(TAG_MEMORY_SIZE);
		buf.write(4);
		byte[] memSize = new byte[4];
		JopProtocolCodec.putInt32(memSize, 0, info.memorySize());
		buf.write(memSize, 0, 4);

		// VERSION
		byte[] versionBytes = info.version().getBytes(StandardCharsets.UTF_8);
		buf.write(TAG_VERSION);
		buf.write(versionBytes.length);
		buf.write(versionBytes, 0, versionBytes.length);

		// PROTOCOL_VERSION
		buf.write(TAG_PROTOCOL_VERSION);
		buf.write(2);
		buf.write(info.protocolMajor());
		buf.write(info.protocolMinor());

		// EXTENDED_REGISTERS
		if (info.extendedRegistersMask() != 0) {
			buf.write(TAG_EXTENDED_REGISTERS);
			buf.write(1);
			buf.write(info.extendedRegistersMask());
		}

		return buf.toByteArray();
	}
}
