package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import org.junit.Test;

import com.jopdesign.core.sim.protocol.Crc8Maxim;
import com.jopdesign.core.sim.protocol.JopMessage;
import com.jopdesign.core.sim.protocol.JopMessageType;
import com.jopdesign.core.sim.protocol.JopProtocolCodec;
import com.jopdesign.core.sim.protocol.JopProtocolException;

/**
 * Tests for {@link JopProtocolCodec} message encoding and decoding.
 */
public class JopProtocolCodecTest {

	// --- Encode tests ---

	@Test
	public void testEncodePing() {
		JopMessage msg = JopMessage.of(JopMessageType.PING, 0);
		byte[] frame = JopProtocolCodec.encode(msg);

		assertEquals(6, frame.length); // SYNC + TYPE + LEN_HI + LEN_LO + CORE + CRC
		assertEquals((byte) 0xA5, frame[0]); // SYNC
		assertEquals((byte) 0xF0, frame[1]); // TYPE = PING
		assertEquals(0, frame[2]); // LEN_HI
		assertEquals(0, frame[3]); // LEN_LO
		assertEquals(0, frame[4]); // CORE = 0

		// CRC over TYPE+LEN+CORE = F0 00 00 00
		int expectedCrc = Crc8Maxim.compute(new byte[] { (byte) 0xF0, 0x00, 0x00, 0x00 });
		assertEquals((byte) expectedCrc, frame[5]);
	}

	@Test
	public void testEncodeHalt() {
		JopMessage msg = JopMessage.of(JopMessageType.HALT, 0);
		byte[] frame = JopProtocolCodec.encode(msg);
		assertEquals(6, frame.length);
		assertEquals((byte) 0x01, frame[1]); // TYPE = HALT
	}

	@Test
	public void testEncodeResume() {
		JopMessage msg = JopMessage.of(JopMessageType.RESUME, 0);
		byte[] frame = JopProtocolCodec.encode(msg);
		assertEquals((byte) 0x02, frame[1]); // TYPE = RESUME
	}

	@Test
	public void testEncodeWithPayload() {
		byte[] payload = { 0x01, 0x02, 0x03 };
		JopMessage msg = JopMessage.of(JopMessageType.WRITE_REGISTER, 0, payload);
		byte[] frame = JopProtocolCodec.encode(msg);

		assertEquals(9, frame.length); // 6 overhead + 3 payload
		assertEquals((byte) 0xA5, frame[0]);
		assertEquals((byte) 0x13, frame[1]); // TYPE = WRITE_REGISTER
		assertEquals(0, frame[2]); // LEN_HI
		assertEquals(3, frame[3]); // LEN_LO
		assertEquals(0x01, frame[5]); // Payload byte 0
		assertEquals(0x02, frame[6]); // Payload byte 1
		assertEquals(0x03, frame[7]); // Payload byte 2
	}

	@Test
	public void testEncodeBroadcastCore() {
		JopMessage msg = JopMessage.of(JopMessageType.HALT, 0xFF);
		byte[] frame = JopProtocolCodec.encode(msg);
		assertEquals((byte) 0xFF, frame[4]); // CORE = broadcast
	}

	@Test
	public void testEncodeSetBreakpoint() {
		byte[] payload = JopProtocolCodec.setBreakpointPayload(0x01, 0x0000002A);
		JopMessage msg = JopMessage.of(JopMessageType.SET_BREAKPOINT, 0, payload);
		byte[] frame = JopProtocolCodec.encode(msg);

		assertEquals(11, frame.length); // 6 + 5
		assertEquals((byte) 0x20, frame[1]); // TYPE = SET_BREAKPOINT
		assertEquals(0, frame[2]); // LEN_HI
		assertEquals(5, frame[3]); // LEN_LO = 5
		assertEquals(0x01, frame[5]); // BP_TYPE = BYTECODE_JPC
		assertEquals(0x00, frame[6]); // ADDR byte 0
		assertEquals(0x00, frame[7]); // ADDR byte 1
		assertEquals(0x00, frame[8]); // ADDR byte 2
		assertEquals(0x2A, frame[9]); // ADDR byte 3
	}

	// --- Decode tests ---

	@Test
	public void testDecodeFrame_Ping() throws JopProtocolException {
		JopMessage original = JopMessage.of(JopMessageType.PING, 0);
		byte[] frame = JopProtocolCodec.encode(original);

		JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
		assertEquals(JopMessageType.PING, decoded.type());
		assertEquals(0, decoded.core());
		assertEquals(0, decoded.payload().length);
	}

	@Test
	public void testDecodeFrame_Pong() throws JopProtocolException {
		JopMessage original = JopMessage.of(JopMessageType.PONG, 0);
		byte[] frame = JopProtocolCodec.encode(original);

		JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
		assertEquals(JopMessageType.PONG, decoded.type());
	}

	@Test
	public void testDecodeFrame_WithPayload() throws JopProtocolException {
		byte[] payload = { 0x01, 0x00, 0x00, 0x00, 0x2A };
		JopMessage original = JopMessage.of(JopMessageType.SET_BREAKPOINT, 0, payload);
		byte[] frame = JopProtocolCodec.encode(original);

		JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
		assertEquals(JopMessageType.SET_BREAKPOINT, decoded.type());
		assertEquals(0, decoded.core());
		assertArrayEquals(payload, decoded.payload());
	}

	@Test
	public void testDecodeFrame_RegistersResponse() throws JopProtocolException {
		// Build a REGISTERS response: 12 registers × 4 bytes = 48 bytes
		byte[] payload = new byte[48];
		JopProtocolCodec.putInt32(payload, 0, 100);   // PC = 100
		JopProtocolCodec.putInt32(payload, 4, 42);    // JPC = 42
		JopProtocolCodec.putInt32(payload, 8, 7);     // A = 7
		JopProtocolCodec.putInt32(payload, 12, 3);    // B = 3
		JopProtocolCodec.putInt32(payload, 16, 64);   // SP = 64
		JopProtocolCodec.putInt32(payload, 20, 32);   // VP = 32
		JopProtocolCodec.putInt32(payload, 24, 0);    // AR = 0
		JopProtocolCodec.putInt32(payload, 28, 0);    // MUL_RESULT = 0

		JopMessage original = JopMessage.of(JopMessageType.REGISTERS, 0, payload);
		byte[] frame = JopProtocolCodec.encode(original);

		JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
		int[] regs = JopProtocolCodec.parseRegisters(decoded.payload());
		assertEquals(12, regs.length);
		assertEquals(100, regs[0]); // PC
		assertEquals(42, regs[1]);  // JPC
		assertEquals(7, regs[2]);   // A
		assertEquals(3, regs[3]);   // B
		assertEquals(64, regs[4]);  // SP
		assertEquals(32, regs[5]);  // VP
	}

	@Test(expected = JopProtocolException.class)
	public void testDecodeFrame_CrcError() throws JopProtocolException {
		JopMessage original = JopMessage.of(JopMessageType.PING, 0);
		byte[] frame = JopProtocolCodec.encode(original);
		frame[frame.length - 1] ^= 0xFF; // Corrupt CRC
		JopProtocolCodec.decodeFrame(frame);
	}

	@Test(expected = JopProtocolException.class)
	public void testDecodeFrame_NoSync() throws JopProtocolException {
		byte[] frame = { 0x00, (byte) 0xF0, 0x00, 0x00, 0x00, 0x00 };
		JopProtocolCodec.decodeFrame(frame);
	}

	@Test(expected = JopProtocolException.class)
	public void testDecodeFrame_Truncated() throws JopProtocolException {
		JopProtocolCodec.decodeFrame(new byte[] { (byte) 0xA5, 0x01 });
	}

	// --- Round-trip tests ---

	@Test
	public void testRoundTrip_AllRequestTypes() throws JopProtocolException {
		int[] requestTypes = {
			JopMessageType.HALT, JopMessageType.RESUME, JopMessageType.STEP_MICRO,
			JopMessageType.STEP_BYTECODE, JopMessageType.RESET, JopMessageType.QUERY_STATUS,
			JopMessageType.READ_REGISTERS, JopMessageType.PING, JopMessageType.QUERY_INFO,
			JopMessageType.QUERY_BREAKPOINTS
		};

		for (int type : requestTypes) {
			JopMessage original = JopMessage.of(type, 0);
			byte[] frame = JopProtocolCodec.encode(original);
			JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
			assertEquals("Round-trip failed for type 0x" + Integer.toHexString(type),
					type, decoded.type());
			assertEquals(0, decoded.core());
			assertEquals(0, decoded.payload().length);
		}
	}

	@Test
	public void testRoundTrip_AllResponseTypes() throws JopProtocolException {
		int[] responseTypes = {
			JopMessageType.ACK, JopMessageType.PONG
		};

		for (int type : responseTypes) {
			JopMessage original = JopMessage.of(type, 0);
			byte[] frame = JopProtocolCodec.encode(original);
			JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
			assertEquals(type, decoded.type());
		}
	}

	@Test
	public void testRoundTrip_Halted() throws JopProtocolException {
		byte[] payload = { 0x01, 0x02 }; // reason=BREAKPOINT, slot=2
		JopMessage original = JopMessage.of(JopMessageType.HALTED, 0, payload);
		byte[] frame = JopProtocolCodec.encode(original);

		JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
		assertEquals(JopMessageType.HALTED, decoded.type());
		int[] parsed = JopProtocolCodec.parseHalted(decoded.payload());
		assertEquals(0x01, parsed[0]); // BREAKPOINT
		assertEquals(0x02, parsed[1]); // slot 2
	}

	@Test
	public void testRoundTrip_Status() throws JopProtocolException {
		byte[] payload = { 0x01, 0x00 }; // state=halted, reason=MANUAL
		JopMessage original = JopMessage.of(JopMessageType.STATUS, 0, payload);
		byte[] frame = JopProtocolCodec.encode(original);

		JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
		int[] parsed = JopProtocolCodec.parseStatus(decoded.payload());
		assertEquals(0x01, parsed[0]); // halted
		assertEquals(0x00, parsed[1]); // MANUAL
	}

	@Test
	public void testRoundTrip_MultiCore() throws JopProtocolException {
		for (int core = 0; core < 4; core++) {
			JopMessage original = JopMessage.of(JopMessageType.READ_REGISTERS, core);
			byte[] frame = JopProtocolCodec.encode(original);
			JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
			assertEquals(core, decoded.core());
		}
	}

	// --- Payload builder tests ---

	@Test
	public void testReadStackPayload() {
		byte[] payload = JopProtocolCodec.readStackPayload(0, 128);
		assertEquals(4, payload.length);
		assertEquals(0, payload[0]); // offset high
		assertEquals(0, payload[1]); // offset low
		assertEquals(0, payload[2]); // count high
		assertEquals((byte) 128, payload[3]); // count low
	}

	@Test
	public void testReadMemoryPayload() {
		byte[] payload = JopProtocolCodec.readMemoryPayload(0x1000, 256);
		assertEquals(8, payload.length);
		assertEquals(0x00, payload[0]);
		assertEquals(0x00, payload[1]);
		assertEquals(0x10, payload[2]);
		assertEquals(0x00, payload[3]);
		assertEquals(0x00, payload[4]);
		assertEquals(0x00, payload[5]);
		assertEquals(0x01, payload[6]);
		assertEquals(0x00, payload[7]);
	}

	@Test
	public void testWriteRegisterPayload() {
		byte[] payload = JopProtocolCodec.writeRegisterPayload(0x04, 64); // SP = 64
		assertEquals(5, payload.length);
		assertEquals(0x04, payload[0]); // reg ID
		assertEquals(0, payload[1]);
		assertEquals(0, payload[2]);
		assertEquals(0, payload[3]);
		assertEquals(64, payload[4]); // value
	}

	@Test
	public void testWriteMemoryBlockPayload() {
		int[] values = { 100, 200, 300 };
		byte[] payload = JopProtocolCodec.writeMemoryBlockPayload(0x1000, values);
		assertEquals(20, payload.length); // 8 header + 3*4 data

		assertEquals(0x1000, JopProtocolCodec.getInt32(payload, 0)); // addr
		assertEquals(3, JopProtocolCodec.getInt32(payload, 4)); // count
		assertEquals(100, JopProtocolCodec.getInt32(payload, 8));
		assertEquals(200, JopProtocolCodec.getInt32(payload, 12));
		assertEquals(300, JopProtocolCodec.getInt32(payload, 16));
	}

	@Test
	public void testClearBreakpointPayload() {
		byte[] payload = JopProtocolCodec.clearBreakpointPayload(3);
		assertEquals(1, payload.length);
		assertEquals(3, payload[0]);
	}

	// --- Payload parser tests ---

	@Test
	public void testParseWordArray() {
		byte[] payload = new byte[12];
		JopProtocolCodec.putInt32(payload, 0, 100);
		JopProtocolCodec.putInt32(payload, 4, 200);
		JopProtocolCodec.putInt32(payload, 8, 300);
		int[] values = JopProtocolCodec.parseWordArray(payload);
		assertEquals(3, values.length);
		assertEquals(100, values[0]);
		assertEquals(200, values[1]);
		assertEquals(300, values[2]);
	}

	@Test
	public void testParseHalted() {
		byte[] payload = { 0x02, (byte) 0xFF }; // STEP_COMPLETE, no slot
		int[] parsed = JopProtocolCodec.parseHalted(payload);
		assertEquals(0x02, parsed[0]);
		assertEquals(0xFF, parsed[1]);
	}

	// --- Message type classification ---

	@Test
	public void testMessageTypeClassification() {
		assertTrue(JopMessageType.isRequest(JopMessageType.HALT));
		assertTrue(JopMessageType.isRequest(JopMessageType.PING));
		assertTrue(JopMessageType.isRequest(JopMessageType.SET_BREAKPOINT));

		assertTrue(JopMessageType.isResponse(JopMessageType.ACK));
		assertTrue(JopMessageType.isResponse(JopMessageType.NAK));
		assertTrue(JopMessageType.isResponse(JopMessageType.REGISTERS));
		assertTrue(JopMessageType.isResponse(JopMessageType.PONG));

		assertTrue(JopMessageType.isNotification(JopMessageType.HALTED));
		assertFalse(JopMessageType.isNotification(JopMessageType.ACK));
		assertFalse(JopMessageType.isNotification(JopMessageType.PING));
	}

	// --- Endianness tests ---

	@Test
	public void testBigEndianInt32() {
		byte[] buf = new byte[4];
		JopProtocolCodec.putInt32(buf, 0, 0x12345678);
		assertEquals(0x12, buf[0] & 0xFF);
		assertEquals(0x34, buf[1] & 0xFF);
		assertEquals(0x56, buf[2] & 0xFF);
		assertEquals(0x78, buf[3] & 0xFF);

		assertEquals(0x12345678, JopProtocolCodec.getInt32(buf, 0));
	}

	@Test
	public void testBigEndianNegativeValue() {
		byte[] buf = new byte[4];
		JopProtocolCodec.putInt32(buf, 0, -1);
		assertEquals((byte) 0xFF, buf[0]);
		assertEquals((byte) 0xFF, buf[1]);
		assertEquals((byte) 0xFF, buf[2]);
		assertEquals((byte) 0xFF, buf[3]);

		assertEquals(-1, JopProtocolCodec.getInt32(buf, 0));
	}
}
