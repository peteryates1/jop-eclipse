package com.jopdesign.core.io;

/**
 * Definition of a pin required by a peripheral.
 *
 * @param name pin signal name (e.g., "gpio[0]", "spi_mosi")
 * @param direction pin direction: "in", "out", or "inout"
 * @param description human-readable description
 */
public record PeripheralPin(
		String name,
		String direction,
		String description
) {}
