package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.junit.Test;

import com.jopdesign.core.sim.protocol.Crc8Maxim;

/**
 * Tests for {@link Crc8Maxim} CRC-8/MAXIM implementation.
 * Reference values verified against the Scala implementation in JopDebugProtocolSim.
 */
public class Crc8MaximTest {

	@Test
	public void testEmptyInput() {
		assertEquals(0, Crc8Maxim.compute(new byte[0]));
	}

	@Test
	public void testSingleByte() {
		// CRC-8/MAXIM of single byte 0x00 = 0x00
		assertEquals(0x00, Crc8Maxim.compute(new byte[] { 0x00 }));
	}

	@Test
	public void testKnownValue_0x01() {
		// CRC-8/MAXIM polynomial 0x8C (reflected) / 0x31 (normal)
		int crc = Crc8Maxim.compute(new byte[] { 0x01 });
		// 0x01 → table[0x01] = 0x5E
		assertEquals(0x5E, crc);
	}

	@Test
	public void testKnownValue_0xFF() {
		int crc = Crc8Maxim.compute(new byte[] { (byte) 0xFF });
		// table[0xFF] = 0x35
		assertEquals(0x35, crc);
	}

	@Test
	public void testMultipleBytes() {
		// "123456789" → CRC-8/MAXIM = 0xA1
		byte[] data = "123456789".getBytes();
		assertEquals(0xA1, Crc8Maxim.compute(data));
	}

	@Test
	public void testUpdateApi() {
		byte[] data = { 0x01, 0x02, 0x03 };
		int expected = Crc8Maxim.compute(data);

		int crc = 0;
		crc = Crc8Maxim.update(crc, 0x01);
		crc = Crc8Maxim.update(crc, 0x02);
		crc = Crc8Maxim.update(crc, 0x03);
		assertEquals(expected, crc);
	}

	@Test
	public void testRangeCompute() {
		byte[] data = { 0x00, 0x01, 0x02, 0x03, 0x00 };
		int full = Crc8Maxim.compute(new byte[] { 0x01, 0x02, 0x03 });
		int range = Crc8Maxim.compute(data, 1, 3);
		assertEquals(full, range);
	}

	@Test
	public void testSelfCheck() {
		// CRC over data + CRC should yield 0
		byte[] data = { 0x01, 0x02, 0x03 };
		int crc = Crc8Maxim.compute(data);
		byte[] dataWithCrc = { 0x01, 0x02, 0x03, (byte) crc };
		assertEquals(0, Crc8Maxim.compute(dataWithCrc));
	}

	@Test
	public void testPingFramePayload() {
		// PING: TYPE=0xF0, LEN=0x0000, CORE=0x00
		// CRC over: F0 00 00 00
		byte[] headerBytes = { (byte) 0xF0, 0x00, 0x00, 0x00 };
		int crc = Crc8Maxim.compute(headerBytes);
		assertTrue("CRC should be in 0x00-0xFF range", crc >= 0 && crc <= 255);
	}
}
