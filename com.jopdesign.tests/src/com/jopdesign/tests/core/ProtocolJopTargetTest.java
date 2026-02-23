package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.IJopTargetListener;
import com.jopdesign.core.sim.JopBreakpointInfo;
import com.jopdesign.core.sim.JopBreakpointType;
import com.jopdesign.core.sim.JopMemoryData;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopStackData;
import com.jopdesign.core.sim.JopSuspendReason;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.JopTargetInfo;
import com.jopdesign.core.sim.JopTargetState;
import com.jopdesign.core.sim.ProtocolJopTarget;
import com.jopdesign.core.sim.protocol.JopMessage;
import com.jopdesign.core.sim.protocol.JopMessageType;
import com.jopdesign.core.sim.protocol.JopProtocolCodec;
import com.jopdesign.core.sim.protocol.JopProtocolException;
import com.jopdesign.core.sim.protocol.JopTargetInfoParser;
import com.jopdesign.core.sim.transport.JopTransport;

/**
 * Tests for {@link ProtocolJopTarget} using a mock transport.
 */
public class ProtocolJopTargetTest {

	private MockTransport mockTransport;
	private ProtocolJopTarget target;

	@Before
	public void setUp() {
		mockTransport = new MockTransport();
		target = new ProtocolJopTarget("Test Target", "test", mockTransport, 0);
	}

	@After
	public void tearDown() {
		try {
			target.disconnect();
		} catch (Exception e) {
			// ignore
		}
	}

	@Test
	public void testNameAndTypeId() {
		assertEquals("Test Target", target.getName());
		assertEquals("test", target.getTargetTypeId());
	}

	@Test
	public void testInitialState() {
		assertEquals(JopTargetState.NOT_STARTED, target.getState());
	}

	@Test
	public void testConnect() throws JopTargetException {
		// Queue responses for connect handshake: PONG, TARGET_INFO, ACK (for HALT)
		mockTransport.queueResponse(JopMessage.of(JopMessageType.PONG, 0));

		JopTargetInfo info = new JopTargetInfo(1, 4, 512, 65536, "test-v1", 1, 0, 0);
		byte[] infoPayload = JopTargetInfoParser.encode(info);
		mockTransport.queueResponse(JopMessage.of(JopMessageType.TARGET_INFO, 0, infoPayload));

		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));

		target.connect();

		assertEquals(JopTargetState.SUSPENDED, target.getState());
		JopTargetInfo returnedInfo = target.getTargetInfo();
		assertEquals(1, returnedInfo.numCores());
		assertEquals(4, returnedInfo.numBreakpoints());
		assertEquals("test-v1", returnedInfo.version());

		// Verify sent messages: PING, QUERY_INFO, HALT
		List<JopMessage> sent = mockTransport.getSentMessages();
		assertTrue(sent.size() >= 3);
		assertEquals(JopMessageType.PING, sent.get(0).type());
		assertEquals(JopMessageType.QUERY_INFO, sent.get(1).type());
		assertEquals(JopMessageType.HALT, sent.get(2).type());
	}

	@Test
	public void testReadRegisters() throws JopTargetException {
		connectTarget();

		// Queue REGISTERS response (12 registers × 4 bytes = 48 bytes)
		byte[] regPayload = new byte[48];
		JopProtocolCodec.putInt32(regPayload, 0, 100);  // PC
		JopProtocolCodec.putInt32(regPayload, 4, 42);   // JPC
		JopProtocolCodec.putInt32(regPayload, 8, 7);    // A
		JopProtocolCodec.putInt32(regPayload, 12, 3);   // B
		JopProtocolCodec.putInt32(regPayload, 16, 64);  // SP
		JopProtocolCodec.putInt32(regPayload, 20, 32);  // VP

		mockTransport.queueResponse(JopMessage.of(JopMessageType.REGISTERS, 0, regPayload));

		JopRegisters regs = target.readRegisters();
		assertEquals(100, regs.pc());
		assertEquals(42, regs.jpc());
		assertEquals(7, regs.a());
		assertEquals(3, regs.b());
		assertEquals(64, regs.sp());
		assertEquals(32, regs.vp());
	}

	@Test
	public void testReadStack() throws JopTargetException {
		connectTarget();

		// First call is readRegisters (inside readStack)
		byte[] regPayload = new byte[48];
		JopProtocolCodec.putInt32(regPayload, 16, 8);  // SP = 8
		mockTransport.queueResponse(JopMessage.of(JopMessageType.REGISTERS, 0, regPayload));

		// Then READ_STACK response
		byte[] stackPayload = new byte[36]; // 9 words (sp+1)
		for (int i = 0; i < 9; i++) {
			JopProtocolCodec.putInt32(stackPayload, i * 4, (i + 1) * 100);
		}
		mockTransport.queueResponse(JopMessage.of(JopMessageType.STACK_DATA, 0, stackPayload));

		JopStackData stack = target.readStack();
		assertEquals(8, stack.sp());
		assertEquals(9, stack.values().length);
		assertEquals(100, stack.values()[0]);
		assertEquals(900, stack.values()[8]);
	}

	@Test
	public void testReadMemory() throws JopTargetException {
		connectTarget();

		byte[] memPayload = new byte[16]; // 4 words
		JopProtocolCodec.putInt32(memPayload, 0, 0xDEADBEEF);
		JopProtocolCodec.putInt32(memPayload, 4, 0xCAFEBABE);
		JopProtocolCodec.putInt32(memPayload, 8, 0x12345678);
		JopProtocolCodec.putInt32(memPayload, 12, 0x00000000);

		mockTransport.queueResponse(JopMessage.of(JopMessageType.MEMORY_DATA, 0, memPayload));

		JopMemoryData mem = target.readMemory(0x1000, 4);
		assertEquals(0x1000, mem.startAddress());
		assertEquals(4, mem.values().length);
		assertEquals(0xDEADBEEF, mem.values()[0]);
		assertEquals(0xCAFEBABE, mem.values()[1]);
	}

	@Test
	public void testSetBreakpoint() throws JopTargetException {
		connectTarget();

		// ACK with slot=2
		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0, new byte[] { 0x02 }));

		int slot = target.setBreakpoint(JopBreakpointType.BYTECODE_JPC, 42);
		assertEquals(2, slot);

		// Verify the sent SET_BREAKPOINT message
		List<JopMessage> sent = mockTransport.getSentMessages();
		JopMessage bpMsg = sent.get(sent.size() - 1);
		assertEquals(JopMessageType.SET_BREAKPOINT, bpMsg.type());
		assertEquals(5, bpMsg.payload().length);
		assertEquals(0x01, bpMsg.payload()[0]); // BYTECODE_JPC
	}

	@Test
	public void testClearBreakpoint() throws JopTargetException {
		connectTarget();

		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));

		target.clearBreakpoint(2);

		List<JopMessage> sent = mockTransport.getSentMessages();
		JopMessage clrMsg = sent.get(sent.size() - 1);
		assertEquals(JopMessageType.CLEAR_BREAKPOINT, clrMsg.type());
		assertEquals(1, clrMsg.payload().length);
		assertEquals(2, clrMsg.payload()[0]);
	}

	@Test
	public void testGetBreakpoints() throws JopTargetException {
		connectTarget();

		// BREAKPOINT_LIST: 4 slots × 6 bytes each (hardware reports all slots)
		// Bit 7 of slot byte = active flag, bits 6:0 = slot number
		byte[] bpPayload = new byte[24];
		bpPayload[0] = (byte) 0x80; // slot 0, active (bit 7 set)
		bpPayload[1] = 0; // MICRO_PC
		JopProtocolCodec.putInt32(bpPayload, 2, 100);
		bpPayload[6] = (byte) 0x81; // slot 1, active (bit 7 set)
		bpPayload[7] = 1; // BYTECODE_JPC
		JopProtocolCodec.putInt32(bpPayload, 8, 200);
		bpPayload[12] = 2; // slot 2, inactive (bit 7 clear)
		bpPayload[13] = 0;
		JopProtocolCodec.putInt32(bpPayload, 14, 0);
		bpPayload[18] = 3; // slot 3, inactive (bit 7 clear)
		bpPayload[19] = 0;
		JopProtocolCodec.putInt32(bpPayload, 20, 0);

		mockTransport.queueResponse(JopMessage.of(JopMessageType.BREAKPOINT_LIST, 0, bpPayload));

		JopBreakpointInfo[] bps = target.getBreakpoints();
		assertEquals(2, bps.length); // only active breakpoints returned
		assertEquals(0, bps[0].slot());
		assertEquals(JopBreakpointType.MICRO_PC, bps[0].type());
		assertEquals(100, bps[0].address());
		assertEquals(1, bps[1].slot());
		assertEquals(JopBreakpointType.BYTECODE_JPC, bps[1].type());
		assertEquals(200, bps[1].address());
	}

	@Test
	public void testResume() throws JopTargetException {
		connectTarget();

		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));

		target.resume();
		assertEquals(JopTargetState.RUNNING, target.getState());

		List<JopMessage> sent = mockTransport.getSentMessages();
		JopMessage resumeMsg = sent.get(sent.size() - 1);
		assertEquals(JopMessageType.RESUME, resumeMsg.type());
	}

	@Test
	public void testSuspend() throws JopTargetException {
		connectTarget();

		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));

		target.suspend();

		List<JopMessage> sent = mockTransport.getSentMessages();
		JopMessage haltMsg = sent.get(sent.size() - 1);
		assertEquals(JopMessageType.HALT, haltMsg.type());
	}

	@Test
	public void testStepMicro() throws JopTargetException {
		connectTarget();

		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));

		target.stepMicro();

		List<JopMessage> sent = mockTransport.getSentMessages();
		JopMessage stepMsg = sent.get(sent.size() - 1);
		assertEquals(JopMessageType.STEP_MICRO, stepMsg.type());
	}

	@Test
	public void testStepBytecode() throws JopTargetException {
		connectTarget();

		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));

		target.stepBytecode();

		List<JopMessage> sent = mockTransport.getSentMessages();
		JopMessage stepMsg = sent.get(sent.size() - 1);
		assertEquals(JopMessageType.STEP_BYTECODE, stepMsg.type());
	}

	@Test
	public void testReset() throws JopTargetException {
		connectTarget();

		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));

		target.reset();

		List<JopMessage> sent = mockTransport.getSentMessages();
		JopMessage resetMsg = sent.get(sent.size() - 1);
		assertEquals(JopMessageType.RESET, resetMsg.type());
	}

	@Test
	public void testDisconnect() throws JopTargetException {
		connectTarget();

		List<JopTargetState> states = new ArrayList<>();
		target.addListener(new IJopTargetListener() {
			@Override
			public void stateChanged(JopTargetState newState, JopSuspendReason reason, int breakpointSlot) {
				states.add(newState);
			}
			@Override
			public void outputProduced(String text) {}
		});

		target.disconnect();
		assertEquals(JopTargetState.TERMINATED, target.getState());
		assertTrue(states.contains(JopTargetState.TERMINATED));
	}

	@Test(expected = JopTargetException.class)
	public void testReadMemoryTooLarge() throws JopTargetException {
		connectTarget();
		target.readMemory(0, 257);
	}

	@Test
	public void testResolveLineToAddress() {
		assertEquals(-1, target.resolveLineToAddress(1));
	}

	@Test
	public void testGetCurrentSourceLine() {
		assertEquals(-1, target.getCurrentSourceLine());
	}

	@Test
	public void testGetCurrentInstructionName() {
		assertNull(target.getCurrentInstructionName());
	}

	// --- Helper methods ---

	private void connectTarget() throws JopTargetException {
		mockTransport.queueResponse(JopMessage.of(JopMessageType.PONG, 0));
		JopTargetInfo info = new JopTargetInfo(1, 4, 512, 65536, "test-v1", 1, 0, 0);
		mockTransport.queueResponse(JopMessage.of(JopMessageType.TARGET_INFO, 0,
				JopTargetInfoParser.encode(info)));
		mockTransport.queueResponse(JopMessage.of(JopMessageType.ACK, 0));
		target.connect();
		mockTransport.clearSentMessages();
	}

	/**
	 * Mock transport that queues responses and records sent messages.
	 */
	private static class MockTransport implements JopTransport {
		private final BlockingQueue<JopMessage> responses = new LinkedBlockingQueue<>();
		private final List<JopMessage> sentMessages = new ArrayList<>();
		private volatile boolean connected = false;
		private volatile NotificationListener notificationListener;

		void queueResponse(JopMessage msg) {
			responses.offer(msg);
		}

		List<JopMessage> getSentMessages() {
			return new ArrayList<>(sentMessages);
		}

		void clearSentMessages() {
			sentMessages.clear();
		}

		@Override
		public void open() {
			connected = true;
		}

		@Override
		public void send(JopMessage message) throws JopProtocolException {
			sentMessages.add(message);
		}

		@Override
		public JopMessage receive(long timeoutMs) throws JopProtocolException {
			try {
				JopMessage msg = responses.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
				if (msg == null) {
					throw new JopProtocolException("Mock transport: no response queued");
				}
				return msg;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new JopProtocolException("Interrupted");
			}
		}

		@Override
		public boolean hasMessage() {
			return !responses.isEmpty();
		}

		@Override
		public boolean isConnected() {
			return connected;
		}

		@Override
		public void close() {
			connected = false;
		}

		@Override
		public void setNotificationListener(NotificationListener listener) {
			this.notificationListener = listener;
		}
	}
}
