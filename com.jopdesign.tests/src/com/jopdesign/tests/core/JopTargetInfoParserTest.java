package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.junit.Test;

import com.jopdesign.core.sim.JopTargetInfo;
import com.jopdesign.core.sim.protocol.JopTargetInfoParser;

/**
 * Tests for {@link JopTargetInfoParser} TLV parser/encoder.
 */
public class JopTargetInfoParserTest {

	@Test
	public void testEmptyPayload() {
		JopTargetInfo info = JopTargetInfoParser.parse(new byte[0]);
		assertEquals(1, info.numCores());
		assertEquals(0, info.numBreakpoints());
		assertEquals(0, info.stackDepth());
		assertEquals(0, info.memorySize());
		assertEquals("unknown", info.version());
		assertEquals(0, info.protocolMajor());
		assertEquals(0, info.protocolMinor());
		assertEquals(0, info.extendedRegistersMask());
	}

	@Test
	public void testRoundTrip() {
		JopTargetInfo original = new JopTargetInfo(2, 4, 1024, 65536, "jop-rtl-v1.0", 1, 0, 1);
		byte[] payload = JopTargetInfoParser.encode(original);
		JopTargetInfo parsed = JopTargetInfoParser.parse(payload);

		assertEquals(original.numCores(), parsed.numCores());
		assertEquals(original.numBreakpoints(), parsed.numBreakpoints());
		assertEquals(original.stackDepth(), parsed.stackDepth());
		assertEquals(original.memorySize(), parsed.memorySize());
		assertEquals(original.version(), parsed.version());
		assertEquals(original.protocolMajor(), parsed.protocolMajor());
		assertEquals(original.protocolMinor(), parsed.protocolMinor());
		assertEquals(original.extendedRegistersMask(), parsed.extendedRegistersMask());
	}

	@Test
	public void testSingleCore_4Breakpoints() {
		JopTargetInfo info = new JopTargetInfo(1, 4, 512, 1048576, "fpga-v2.1", 1, 1, 0);
		byte[] payload = JopTargetInfoParser.encode(info);
		JopTargetInfo parsed = JopTargetInfoParser.parse(payload);

		assertEquals(1, parsed.numCores());
		assertEquals(4, parsed.numBreakpoints());
		assertEquals(512, parsed.stackDepth());
		assertEquals(1048576, parsed.memorySize());
		assertEquals("fpga-v2.1", parsed.version());
		assertEquals(1, parsed.protocolMajor());
		assertEquals(1, parsed.protocolMinor());
	}

	@Test
	public void testExtendedRegisters() {
		JopTargetInfo info = new JopTargetInfo(1, 4, 256, 1024, "test", 1, 0, 0x01);
		byte[] payload = JopTargetInfoParser.encode(info);
		JopTargetInfo parsed = JopTargetInfoParser.parse(payload);
		assertEquals(0x01, parsed.extendedRegistersMask());
	}

	@Test
	public void testNoExtendedRegisters() {
		JopTargetInfo info = new JopTargetInfo(1, 4, 256, 1024, "test", 1, 0, 0);
		byte[] payload = JopTargetInfoParser.encode(info);
		JopTargetInfo parsed = JopTargetInfoParser.parse(payload);
		assertEquals(0, parsed.extendedRegistersMask());
	}

	@Test
	public void testUnknownTagsIgnored() {
		// Add an unknown tag 0xFE with 2 bytes of data
		byte[] payload = {
			0x01, 0x01, 0x02,    // NUM_CORES = 2
			(byte) 0xFE, 0x02, 0x11, 0x22,  // Unknown tag
			0x02, 0x01, 0x04,    // NUM_BREAKPOINTS = 4
		};
		JopTargetInfo parsed = JopTargetInfoParser.parse(payload);
		assertEquals(2, parsed.numCores());
		assertEquals(4, parsed.numBreakpoints());
	}

	@Test
	public void testTruncatedTlv() {
		// Payload claims LEN=4 but only 2 bytes follow
		byte[] payload = {
			0x01, 0x01, 0x01,    // NUM_CORES = 1
			0x04, 0x04, 0x00, 0x01 // MEMORY_SIZE tag with len=4 but only 2 bytes
		};
		// Should parse first tag and stop gracefully
		JopTargetInfo parsed = JopTargetInfoParser.parse(payload);
		assertEquals(1, parsed.numCores());
	}

	@Test
	public void testManualPayload() {
		// Build payload by hand, matching what hardware would send
		byte[] payload = {
			0x01, 0x01, 0x01,                // NUM_CORES = 1
			0x02, 0x01, 0x04,                // NUM_BREAKPOINTS = 4
			0x03, 0x02, 0x02, 0x00,          // STACK_DEPTH = 512
			0x04, 0x04, 0x00, 0x01, 0x00, 0x00, // MEMORY_SIZE = 65536
			0x08, 0x02, 0x01, 0x00,          // PROTOCOL_VERSION = 1.0
			0x07, 0x03, 0x31, 0x2E, 0x30,    // VERSION = "1.0"
		};

		JopTargetInfo parsed = JopTargetInfoParser.parse(payload);
		assertEquals(1, parsed.numCores());
		assertEquals(4, parsed.numBreakpoints());
		assertEquals(512, parsed.stackDepth());
		assertEquals(65536, parsed.memorySize());
		assertEquals("1.0", parsed.version());
		assertEquals(1, parsed.protocolMajor());
		assertEquals(0, parsed.protocolMinor());
	}
}
