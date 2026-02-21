package com.jopdesign.core.io;

import java.util.List;

/**
 * Immutable definition of a JOP IO peripheral.
 *
 * <p>JOP's IO space uses 4 slave slots addressed by bits [5:4] of the
 * 8-bit IO address. Each slave has 16 registers (bits [3:0]).
 * Slave 0 is always the system controller (BmbSys) and slave 1 is
 * always UART (BmbUart). Slots 2 and 3 are available for user-selected
 * peripherals.
 *
 * @param id unique peripheral identifier (e.g., "gpio", "spi")
 * @param name human-readable name (e.g., "GPIO", "SPI Master")
 * @param defaultSlot default IO slave slot (0-3); fixed for system/UART
 * @param fixed true if the peripheral cannot be removed or moved (system, UART)
 * @param driverClassName Java driver class name (e.g., "GpioDevice")
 * @param spinalClassName SpinalHDL component class (e.g., "BmbGpio")
 * @param description human-readable description
 * @param registers list of register definitions
 * @param pins list of required pins
 */
public record PeripheralDefinition(
		String id,
		String name,
		int defaultSlot,
		boolean fixed,
		String driverClassName,
		String spinalClassName,
		String description,
		List<PeripheralRegister> registers,
		List<PeripheralPin> pins
) {
	public PeripheralDefinition {
		if (defaultSlot < 0 || defaultSlot > 3) {
			throw new IllegalArgumentException("Slave slot must be 0-3, got " + defaultSlot);
		}
		registers = List.copyOf(registers);
		pins = List.copyOf(pins);
	}

	/** Base IO address for this peripheral's default slot (slot * 16). */
	public int baseAddress() {
		return defaultSlot * 16;
	}

	/** Base IO address for a given slot assignment. */
	public int baseAddress(int slot) {
		return slot * 16;
	}

	/** Format the address range string for a given slot (e.g., "0x20-0x2F"). */
	public String addressRange(int slot) {
		int base = slot * 16;
		return String.format("0x%02X-0x%02X", base, base + 15);
	}
}
