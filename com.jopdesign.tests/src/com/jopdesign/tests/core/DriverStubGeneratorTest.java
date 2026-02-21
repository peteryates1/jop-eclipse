package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.jopdesign.core.io.DriverStubGenerator;
import com.jopdesign.core.io.PeripheralDefinition;
import com.jopdesign.core.io.PeripheralRegistry;

/**
 * Tests for {@link DriverStubGenerator} — Java driver stub code generation.
 */
public class DriverStubGeneratorTest {

	@Test
	public void testGenerateGpioDriver() {
		PeripheralDefinition gpio = PeripheralRegistry.getPeripheral("gpio");
		String source = DriverStubGenerator.generate(gpio, 2, "jop.io");

		assertTrue("Should have package declaration",
				source.contains("package jop.io;"));
		assertTrue("Should import HardwareObject",
				source.contains("import com.jopdesign.sys.HardwareObject;"));
		assertTrue("Should extend HardwareObject",
				source.contains("class GpioDevice extends HardwareObject"));
		assertTrue("Should have dir field",
				source.contains("public volatile int dir;"));
		assertTrue("Should have out field",
				source.contains("public volatile int out;"));
		assertTrue("Should have in field",
				source.contains("public volatile int in;"));
		assertTrue("Should have getInstance factory",
				source.contains("public static GpioDevice getInstance()"));
		assertTrue("Should reference slot 2 address",
				source.contains("0x20"));
	}

	@Test
	public void testGenerateSpiDriver() {
		PeripheralDefinition spi = PeripheralRegistry.getPeripheral("spi");
		String source = DriverStubGenerator.generate(spi, 3, "jop.io");

		assertTrue(source.contains("class SpiDevice extends HardwareObject"));
		assertTrue("Should have status field",
				source.contains("public volatile int status;"));
		assertTrue("Should have config field",
				source.contains("public volatile int config;"));
		assertTrue("Should have txData field",
				source.contains("public volatile int txData;"));
		assertTrue("Should reference slot 3 address",
				source.contains("0x30"));
	}

	@Test
	public void testGenerateTimerDriver() {
		PeripheralDefinition timer = PeripheralRegistry.getPeripheral("timer");
		String source = DriverStubGenerator.generate(timer, 3, "com.myapp.hw");

		assertTrue(source.contains("package com.myapp.hw;"));
		assertTrue(source.contains("class TimerDevice extends HardwareObject"));
		assertTrue(source.contains("public volatile int ch0Cnt;"));
		assertTrue(source.contains("public volatile int ch0Cmp;"));
		assertTrue(source.contains("public volatile int ch1Cnt;"));
	}

	@Test
	public void testGenerateAll() {
		Map<String, Integer> enabled = new LinkedHashMap<>();
		enabled.put("gpio", 2);
		enabled.put("spi", 3);

		Map<String, String> sources = DriverStubGenerator.generateAll(
				enabled, "jop.io");

		assertEquals(2, sources.size());
		assertTrue(sources.containsKey("GpioDevice.java"));
		assertTrue(sources.containsKey("SpiDevice.java"));

		// Fixed peripherals (sys, uart) should NOT be generated
		assertFalse(sources.containsKey("SysDevice.java"));
		assertFalse(sources.containsKey("SerialPort.java"));
	}

	@Test
	public void testGenerateAllSkipsFixed() {
		Map<String, Integer> enabled = new LinkedHashMap<>();
		enabled.put("sys", 0);  // fixed — should be skipped
		enabled.put("gpio", 2);

		Map<String, String> sources = DriverStubGenerator.generateAll(
				enabled, "jop.io");

		assertEquals(1, sources.size());
		assertTrue(sources.containsKey("GpioDevice.java"));
	}

	@Test
	public void testGeneratePinConstraintsQuartus() {
		Map<String, Integer> enabled = new LinkedHashMap<>();
		enabled.put("gpio", 2);
		enabled.put("spi", 3);

		String qsf = DriverStubGenerator.generatePinConstraints(enabled, "quartus");

		assertTrue(qsf.contains("QSF format"));
		assertTrue(qsf.contains("GPIO"));
		assertTrue(qsf.contains("SPI Master"));
		assertTrue(qsf.contains("set_location_assignment"));
		assertTrue(qsf.contains("gpio[0]"));
		assertTrue(qsf.contains("spi_clk"));
		assertTrue(qsf.contains("spi_mosi"));
	}

	@Test
	public void testGeneratePinConstraintsVivado() {
		Map<String, Integer> enabled = new LinkedHashMap<>();
		enabled.put("i2c", 2);

		String xdc = DriverStubGenerator.generatePinConstraints(enabled, "vivado");

		assertTrue(xdc.contains("XDC format"));
		assertTrue(xdc.contains("I2C Master"));
		assertTrue(xdc.contains("set_property PACKAGE_PIN"));
		assertTrue(xdc.contains("i2c_scl"));
		assertTrue(xdc.contains("i2c_sda"));
	}

	@Test
	public void testGeneratePinConstraintsSkipsNoPins() {
		Map<String, Integer> enabled = new LinkedHashMap<>();
		enabled.put("timer", 3); // timer has no pins

		String qsf = DriverStubGenerator.generatePinConstraints(enabled, "quartus");
		// Should have header but no pin assignments
		assertTrue(qsf.contains("QSF format"));
		assertFalse(qsf.contains("set_location_assignment"));
	}

	@Test
	public void testFieldNameConversion() {
		// Test indirectly via generated source — TX_DATA should become txData
		PeripheralDefinition spi = PeripheralRegistry.getPeripheral("spi");
		String source = DriverStubGenerator.generate(spi, 2, "jop.io");
		assertTrue("TX_DATA should become txData field",
				source.contains("public volatile int txData;"));
		assertTrue("RX_DATA should become rxData field",
				source.contains("public volatile int rxData;"));
		assertTrue("STATUS should become status field",
				source.contains("public volatile int status;"));
	}

	@Test
	public void testDriverHasAccessorMethods() {
		PeripheralDefinition spi = PeripheralRegistry.getPeripheral("spi");
		String source = DriverStubGenerator.generate(spi, 2, "jop.io");

		// Read-only STATUS register should have readStatus()
		assertTrue(source.contains("readStatus()"));
		// Write-only TX_DATA should have writeTxData()
		assertTrue(source.contains("writeTxData(int value)"));
		// Read-only RX_DATA should have readRxData()
		assertTrue(source.contains("readRxData()"));
	}

	@Test
	public void testDriverAddressInJavadoc() {
		PeripheralDefinition gpio = PeripheralRegistry.getPeripheral("gpio");
		String source = DriverStubGenerator.generate(gpio, 2, "jop.io");

		assertTrue("Javadoc should show address range",
				source.contains("0x20-0x2F"));
		assertTrue("Javadoc should show slot number",
				source.contains("Slave slot 2"));
	}
}
