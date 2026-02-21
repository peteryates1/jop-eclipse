package com.jopdesign.core.io;

/**
 * Definition of a single IO register within a peripheral.
 *
 * @param offset register offset within the peripheral's address range (0-15)
 * @param name register name (e.g., "STATUS", "TX_DATA")
 * @param access access mode: "R" (read-only), "W" (write-only), or "RW" (read-write)
 * @param width register width in bits (typically 32)
 * @param description human-readable description of the register
 */
public record PeripheralRegister(
		int offset,
		String name,
		String access,
		int width,
		String description
) {
	public PeripheralRegister {
		if (offset < 0 || offset > 15) {
			throw new IllegalArgumentException("Register offset must be 0-15, got " + offset);
		}
	}
}
