package com.jopdesign.core.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of available JOP IO peripherals.
 *
 * <p>Provides definitions for built-in peripherals (system controller,
 * UART) and optional peripherals (GPIO, SPI, I2C, Timer) that can be
 * selected in the board configuration UI.
 */
public final class PeripheralRegistry {

	private static final Map<String, PeripheralDefinition> PERIPHERALS;

	static {
		Map<String, PeripheralDefinition> map = new LinkedHashMap<>();

		// ---- Slot 0: System Controller (fixed) ----
		map.put("sys", new PeripheralDefinition(
				"sys", "System Controller", 0, true,
				"SysDevice", "BmbSys",
				"System controller: cycle counter, timer, watchdog, interrupt control, CPU ID",
				List.of(
						new PeripheralRegister(0, "CNT", "R", 32, "Clock cycle counter (low 32 bits)"),
						new PeripheralRegister(1, "TIMER_INT", "RW", 32, "Timer interrupt threshold"),
						new PeripheralRegister(2, "INT_ENA", "RW", 32, "Interrupt enable mask"),
						new PeripheralRegister(3, "INT_PEND", "R", 32, "Pending interrupts"),
						new PeripheralRegister(4, "WD_VALUE", "W", 32, "Watchdog reload value"),
						new PeripheralRegister(5, "EXCEPTION", "R", 32, "Last exception code"),
						new PeripheralRegister(6, "LOCK", "RW", 32, "Hardware lock for CMP synchronisation"),
						new PeripheralRegister(7, "CPU_ID", "R", 32, "CPU core ID (0 = boot core)"),
						new PeripheralRegister(8, "CPU_CNT", "R", 32, "Total CPU core count"),
						new PeripheralRegister(9, "CNT_HI", "R", 32, "Clock cycle counter (high 32 bits)")
				),
				List.of()
		));

		// ---- Slot 1: UART (fixed) ----
		map.put("uart", new PeripheralDefinition(
				"uart", "UART", 1, true,
				"SerialPort", "BmbUart",
				"UART serial port with 16-entry TX/RX FIFOs, configurable baud rate",
				List.of(
						new PeripheralRegister(0, "STATUS", "R", 32,
								"Status: bit 0 = TX ready, bit 1 = RX available"),
						new PeripheralRegister(1, "TX_DATA", "W", 32, "Transmit data (write byte to send)"),
						new PeripheralRegister(2, "RX_DATA", "R", 32, "Receive data (read received byte)")
				),
				List.of(
						new PeripheralPin("uart_tx", "out", "UART transmit"),
						new PeripheralPin("uart_rx", "in", "UART receive")
				)
		));

		// ---- Slot 2/3: GPIO (optional) ----
		List<PeripheralPin> gpioPins = new ArrayList<>();
		for (int i = 0; i < 32; i++) {
			gpioPins.add(new PeripheralPin("gpio[" + i + "]", "inout",
					"General purpose IO pin " + i));
		}
		map.put("gpio", new PeripheralDefinition(
				"gpio", "GPIO", 2, false,
				"GpioDevice", "BmbGpio",
				"32-bit general purpose IO with real-time guarantees. "
						+ "Direction, output value, and input read per pin.",
				List.of(
						new PeripheralRegister(0, "DIR", "RW", 32,
								"Direction register: 1=output, 0=input"),
						new PeripheralRegister(1, "OUT", "RW", 32,
								"Output register: value driven on output pins"),
						new PeripheralRegister(2, "IN", "R", 32,
								"Input register: current value of all pins"),
						new PeripheralRegister(3, "SET", "W", 32,
								"Atomic set: write 1 to set output bits"),
						new PeripheralRegister(4, "CLR", "W", 32,
								"Atomic clear: write 1 to clear output bits"),
						new PeripheralRegister(5, "TOGGLE", "W", 32,
								"Atomic toggle: write 1 to toggle output bits"),
						new PeripheralRegister(6, "INT_ENA", "RW", 32,
								"Interrupt enable per pin"),
						new PeripheralRegister(7, "INT_PEND", "R", 32,
								"Interrupt pending per pin (write 1 to clear)")
				),
				gpioPins
		));

		// ---- Slot 2/3: SPI Master (optional) ----
		map.put("spi", new PeripheralDefinition(
				"spi", "SPI Master", 2, false,
				"SpiDevice", "BmbSpi",
				"SPI master with configurable clock divider, CPOL/CPHA modes, "
						+ "and chip select control.",
				List.of(
						new PeripheralRegister(0, "STATUS", "R", 32,
								"Status: bit 0 = TX ready, bit 1 = RX available, bit 2 = busy"),
						new PeripheralRegister(1, "CONFIG", "RW", 32,
								"Config: bits [7:0] = clock divider, bit 8 = CPOL, bit 9 = CPHA"),
						new PeripheralRegister(2, "TX_DATA", "W", 32,
								"Transmit data (write starts transfer)"),
						new PeripheralRegister(3, "RX_DATA", "R", 32,
								"Receive data (read after transfer complete)"),
						new PeripheralRegister(4, "CS", "RW", 32,
								"Chip select: bit mask of active-low CS lines")
				),
				List.of(
						new PeripheralPin("spi_clk", "out", "SPI clock"),
						new PeripheralPin("spi_mosi", "out", "SPI master out, slave in"),
						new PeripheralPin("spi_miso", "in", "SPI master in, slave out"),
						new PeripheralPin("spi_cs_n[0]", "out", "SPI chip select 0 (active low)")
				)
		));

		// ---- Slot 2/3: I2C Master (optional) ----
		map.put("i2c", new PeripheralDefinition(
				"i2c", "I2C Master", 2, false,
				"I2cDevice", "BmbI2c",
				"I2C master with configurable clock speed (100kHz/400kHz), "
						+ "7-bit addressing, and automatic start/stop generation.",
				List.of(
						new PeripheralRegister(0, "STATUS", "R", 32,
								"Status: bit 0 = busy, bit 1 = ACK received, bit 2 = error"),
						new PeripheralRegister(1, "CONFIG", "RW", 32,
								"Config: bits [7:0] = prescaler for SCL clock"),
						new PeripheralRegister(2, "TX_DATA", "W", 32,
								"Transmit data: bits [7:0] = data, bit 8 = start, bit 9 = stop"),
						new PeripheralRegister(3, "RX_DATA", "R", 32,
								"Received data byte"),
						new PeripheralRegister(4, "CMD", "W", 32,
								"Command: 0=write, 1=read, 2=read with NACK (last byte)")
				),
				List.of(
						new PeripheralPin("i2c_scl", "inout", "I2C clock (open-drain)"),
						new PeripheralPin("i2c_sda", "inout", "I2C data (open-drain)")
				)
		));

		// ---- Slot 2/3: Timer / Counter (optional) ----
		map.put("timer", new PeripheralDefinition(
				"timer", "Timer / Counter", 3, false,
				"TimerDevice", "BmbTimer",
				"Multi-channel timer/counter with compare match and capture modes. "
						+ "Two independent 32-bit channels with interrupt generation.",
				List.of(
						new PeripheralRegister(0, "STATUS", "R", 32,
								"Status: bit 0 = CH0 match, bit 1 = CH1 match"),
						new PeripheralRegister(1, "CONFIG", "RW", 32,
								"Config: bit 0 = CH0 enable, bit 1 = CH1 enable, "
										+ "bit 2 = CH0 auto-reload, bit 3 = CH1 auto-reload"),
						new PeripheralRegister(2, "PRESCALER", "RW", 32,
								"Clock prescaler (timer_clk = sys_clk / (prescaler + 1))"),
						new PeripheralRegister(3, "CH0_CNT", "R", 32,
								"Channel 0 counter value"),
						new PeripheralRegister(4, "CH0_CMP", "RW", 32,
								"Channel 0 compare value (triggers interrupt on match)"),
						new PeripheralRegister(5, "CH0_LOAD", "W", 32,
								"Channel 0 reload value for auto-reload mode"),
						new PeripheralRegister(6, "CH1_CNT", "R", 32,
								"Channel 1 counter value"),
						new PeripheralRegister(7, "CH1_CMP", "RW", 32,
								"Channel 1 compare value"),
						new PeripheralRegister(8, "CH1_LOAD", "W", 32,
								"Channel 1 reload value"),
						new PeripheralRegister(9, "INT_ENA", "RW", 32,
								"Interrupt enable: bit 0 = CH0, bit 1 = CH1"),
						new PeripheralRegister(10, "INT_CLR", "W", 32,
								"Interrupt clear: write 1 to clear pending interrupt")
				),
				List.of()
		));

		PERIPHERALS = Collections.unmodifiableMap(map);
	}

	private PeripheralRegistry() {}

	/** Get all registered peripherals, keyed by ID. */
	public static Map<String, PeripheralDefinition> getPeripherals() {
		return PERIPHERALS;
	}

	/** Get a peripheral by ID, or null if not found. */
	public static PeripheralDefinition getPeripheral(String id) {
		return PERIPHERALS.get(id);
	}

	/** Get all peripheral IDs in display order. */
	public static List<String> getPeripheralIds() {
		return new ArrayList<>(PERIPHERALS.keySet());
	}

	/** Get only the optional (non-fixed) peripherals. */
	public static List<PeripheralDefinition> getOptionalPeripherals() {
		List<PeripheralDefinition> result = new ArrayList<>();
		for (PeripheralDefinition p : PERIPHERALS.values()) {
			if (!p.fixed()) {
				result.add(p);
			}
		}
		return result;
	}

	/** Get only the fixed (always-present) peripherals. */
	public static List<PeripheralDefinition> getFixedPeripherals() {
		List<PeripheralDefinition> result = new ArrayList<>();
		for (PeripheralDefinition p : PERIPHERALS.values()) {
			if (p.fixed()) {
				result.add(p);
			}
		}
		return result;
	}
}
