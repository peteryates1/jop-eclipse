package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import com.jopdesign.core.io.PeripheralDefinition;
import com.jopdesign.core.io.PeripheralRegistry;

/**
 * Tests for {@link PeripheralRegistry} — peripheral definitions and queries.
 */
public class PeripheralRegistryTest {

	@Test
	public void testPeripheralCount() {
		// sys, uart, gpio, spi, i2c, timer
		assertEquals(6, PeripheralRegistry.getPeripherals().size());
	}

	@Test
	public void testFixedPeripherals() {
		List<PeripheralDefinition> fixed = PeripheralRegistry.getFixedPeripherals();
		assertEquals(2, fixed.size());
		assertEquals("sys", fixed.get(0).id());
		assertEquals("uart", fixed.get(1).id());
	}

	@Test
	public void testOptionalPeripherals() {
		List<PeripheralDefinition> optional = PeripheralRegistry.getOptionalPeripherals();
		assertEquals(4, optional.size());
		List<String> ids = optional.stream().map(PeripheralDefinition::id).toList();
		assertTrue(ids.contains("gpio"));
		assertTrue(ids.contains("spi"));
		assertTrue(ids.contains("i2c"));
		assertTrue(ids.contains("timer"));
	}

	@Test
	public void testSystemControllerDefinition() {
		PeripheralDefinition sys = PeripheralRegistry.getPeripheral("sys");
		assertNotNull(sys);
		assertEquals("System Controller", sys.name());
		assertEquals(0, sys.defaultSlot());
		assertTrue(sys.fixed());
		assertEquals("SysDevice", sys.driverClassName());
		assertEquals("BmbSys", sys.spinalClassName());
		assertTrue(sys.registers().size() >= 10);
		assertTrue(sys.pins().isEmpty());
	}

	@Test
	public void testUartDefinition() {
		PeripheralDefinition uart = PeripheralRegistry.getPeripheral("uart");
		assertNotNull(uart);
		assertEquals(1, uart.defaultSlot());
		assertTrue(uart.fixed());
		assertEquals(3, uart.registers().size());
		assertEquals(2, uart.pins().size());
	}

	@Test
	public void testGpioDefinition() {
		PeripheralDefinition gpio = PeripheralRegistry.getPeripheral("gpio");
		assertNotNull(gpio);
		assertEquals(2, gpio.defaultSlot());
		assertFalse(gpio.fixed());
		assertEquals("GpioDevice", gpio.driverClassName());
		assertEquals(8, gpio.registers().size());
		assertEquals(32, gpio.pins().size()); // 32 GPIO pins
	}

	@Test
	public void testSpiDefinition() {
		PeripheralDefinition spi = PeripheralRegistry.getPeripheral("spi");
		assertNotNull(spi);
		assertFalse(spi.fixed());
		assertEquals(5, spi.registers().size());
		assertEquals(4, spi.pins().size()); // clk, mosi, miso, cs
	}

	@Test
	public void testI2cDefinition() {
		PeripheralDefinition i2c = PeripheralRegistry.getPeripheral("i2c");
		assertNotNull(i2c);
		assertFalse(i2c.fixed());
		assertEquals(5, i2c.registers().size());
		assertEquals(2, i2c.pins().size()); // scl, sda
	}

	@Test
	public void testTimerDefinition() {
		PeripheralDefinition timer = PeripheralRegistry.getPeripheral("timer");
		assertNotNull(timer);
		assertEquals(3, timer.defaultSlot());
		assertFalse(timer.fixed());
		assertEquals(11, timer.registers().size());
		assertTrue(timer.pins().isEmpty()); // no external pins
	}

	@Test
	public void testAddressRanges() {
		PeripheralDefinition sys = PeripheralRegistry.getPeripheral("sys");
		assertEquals("0x00-0x0F", sys.addressRange(0));

		PeripheralDefinition uart = PeripheralRegistry.getPeripheral("uart");
		assertEquals("0x10-0x1F", uart.addressRange(1));

		PeripheralDefinition gpio = PeripheralRegistry.getPeripheral("gpio");
		assertEquals("0x20-0x2F", gpio.addressRange(2));
		assertEquals("0x30-0x3F", gpio.addressRange(3));
	}

	@Test
	public void testRegisterOffsets() {
		PeripheralDefinition sys = PeripheralRegistry.getPeripheral("sys");
		// First register should be offset 0
		assertEquals(0, sys.registers().get(0).offset());
		assertEquals("CNT", sys.registers().get(0).name());
	}

	@Test
	public void testGetPeripheralNotFound() {
		assertNull(PeripheralRegistry.getPeripheral("nonexistent"));
	}

	@Test
	public void testPeripheralIds() {
		List<String> ids = PeripheralRegistry.getPeripheralIds();
		assertEquals(6, ids.size());
		// Should be in insertion order
		assertEquals("sys", ids.get(0));
		assertEquals("uart", ids.get(1));
	}
}
