package com.jopdesign.core.sim.protocol;

/**
 * CRC-8/MAXIM (Dallas/iButton) implementation.
 * Polynomial: x^8 + x^5 + x^4 + 1 (0x8C reflected, 0x31 normal).
 *
 * <p>This matches the hardware implementation in DebugProtocol.scala
 * (Crc8Maxim component) and the Scala test reference in
 * JopDebugProtocolSim.scala.
 */
public final class Crc8Maxim {

	private Crc8Maxim() {}

	private static final int[] TABLE = new int[256];

	static {
		for (int i = 0; i < 256; i++) {
			int crc = i;
			for (int bit = 0; bit < 8; bit++) {
				if ((crc & 1) != 0) {
					crc = (crc >>> 1) ^ 0x8C;
				} else {
					crc = crc >>> 1;
				}
			}
			TABLE[i] = crc & 0xFF;
		}
	}

	/**
	 * Compute CRC-8/MAXIM over a byte array.
	 *
	 * @param data the data bytes
	 * @return the CRC value (0x00 = no error when applied to data+crc)
	 */
	public static int compute(byte[] data) {
		return compute(data, 0, data.length);
	}

	/**
	 * Compute CRC-8/MAXIM over a range of bytes.
	 *
	 * @param data   the data buffer
	 * @param offset start offset
	 * @param length number of bytes
	 * @return the CRC value
	 */
	public static int compute(byte[] data, int offset, int length) {
		int crc = 0;
		for (int i = offset; i < offset + length; i++) {
			crc = TABLE[(crc ^ data[i]) & 0xFF];
		}
		return crc;
	}

	/**
	 * Update an existing CRC with a single byte.
	 *
	 * @param crc  current CRC value
	 * @param b    next byte
	 * @return updated CRC value
	 */
	public static int update(int crc, int b) {
		return TABLE[(crc ^ b) & 0xFF];
	}
}
