package com.jopdesign.tests.core;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jopdesign.core.sim.protocol.JopMessage;
import com.jopdesign.core.sim.protocol.JopMessageType;
import com.jopdesign.core.sim.protocol.JopProtocolCodec;
import com.jopdesign.core.sim.protocol.JopProtocolException;
import com.jopdesign.core.sim.transport.JopSerialTransport;

/**
 * Tests for {@link JopSerialTransport} using piped streams as loopback.
 * Verifies message framing, notification demuxing, and error handling.
 */
public class JopTransportTest {

	private PipedInputStream transportIn;
	private PipedOutputStream transportOut;
	private PipedInputStream feedbackIn;
	private PipedOutputStream feedbackOut;
	private JopSerialTransport transport;

	@Before
	public void setUp() throws IOException {
		// Create two pipe pairs:
		// transport reads from transportIn (we write to feedbackOut → transportIn)
		// transport writes to transportOut (we read from feedbackIn ← transportOut)
		transportIn = new PipedInputStream(8192);
		feedbackOut = new PipedOutputStream(transportIn);

		feedbackIn = new PipedInputStream(8192);
		transportOut = new PipedOutputStream(feedbackIn);

		transport = new JopSerialTransport("/dev/testPort");
		transport.open(transportIn, transportOut, null);
	}

	@After
	public void tearDown() {
		transport.close();
		try { feedbackOut.close(); } catch (IOException e) { /* ignore */ }
		try { feedbackIn.close(); } catch (IOException e) { /* ignore */ }
	}

	@Test
	public void testSendMessage() throws JopProtocolException, IOException {
		JopMessage msg = JopMessage.of(JopMessageType.PING, 0);
		transport.send(msg);

		// Read the frame from the feedback pipe
		byte[] frame = new byte[6]; // PING has no payload
		int read = feedbackIn.read(frame);
		assertEquals(6, read);
		assertEquals((byte) 0xA5, frame[0]);
		assertEquals((byte) 0xF0, frame[1]);

		// Verify it decodes correctly
		JopMessage decoded = JopProtocolCodec.decodeFrame(frame);
		assertEquals(JopMessageType.PING, decoded.type());
	}

	@Test
	public void testReceiveResponse() throws JopProtocolException, IOException {
		// Feed a PONG response into the transport's input
		JopMessage pong = JopMessage.of(JopMessageType.PONG, 0);
		byte[] frame = JopProtocolCodec.encode(pong);
		feedbackOut.write(frame);
		feedbackOut.flush();

		// Transport should receive it (the read thread demuxes)
		JopMessage received = transport.receive(2000);
		assertEquals(JopMessageType.PONG, received.type());
	}

	@Test
	public void testReceiveNotification() throws JopProtocolException, IOException, InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<JopMessage> notification = new AtomicReference<>();

		transport.setNotificationListener(msg -> {
			notification.set(msg);
			latch.countDown();
		});

		// Feed a HALTED notification
		byte[] payload = { 0x01, 0x00 }; // reason=BREAKPOINT, slot=0
		JopMessage halted = JopMessage.of(JopMessageType.HALTED, 0, payload);
		byte[] frame = JopProtocolCodec.encode(halted);
		feedbackOut.write(frame);
		feedbackOut.flush();

		assertTrue("Notification not received within timeout", latch.await(2, TimeUnit.SECONDS));
		assertNotNull(notification.get());
		assertEquals(JopMessageType.HALTED, notification.get().type());
	}

	@Test
	public void testDemuxResponsesAndNotifications() throws Exception {
		CountDownLatch notifLatch = new CountDownLatch(1);
		AtomicReference<JopMessage> notification = new AtomicReference<>();

		transport.setNotificationListener(msg -> {
			notification.set(msg);
			notifLatch.countDown();
		});

		// Feed an ACK response followed by a HALTED notification
		JopMessage ack = JopMessage.of(JopMessageType.ACK, 0);
		JopMessage halted = JopMessage.of(JopMessageType.HALTED, 0, new byte[] { 0x02, (byte) 0xFF });

		feedbackOut.write(JopProtocolCodec.encode(ack));
		feedbackOut.write(JopProtocolCodec.encode(halted));
		feedbackOut.flush();

		// Response should go to receive queue
		JopMessage resp = transport.receive(2000);
		assertEquals(JopMessageType.ACK, resp.type());

		// Notification should go to listener
		assertTrue(notifLatch.await(2, TimeUnit.SECONDS));
		assertEquals(JopMessageType.HALTED, notification.get().type());
	}

	@Test
	public void testIsConnected() {
		assertTrue(transport.isConnected());
		transport.close();
		assertFalse(transport.isConnected());
	}

	@Test
	public void testSendAfterClose() {
		transport.close();
		try {
			transport.send(JopMessage.of(JopMessageType.PING, 0));
			fail("Should throw on send after close");
		} catch (JopProtocolException e) {
			assertTrue(e.getMessage().contains("not connected"));
		}
	}

	@Test
	public void testMultipleResponses() throws Exception {
		// Feed 3 ACK responses
		for (int i = 0; i < 3; i++) {
			byte[] frame = JopProtocolCodec.encode(JopMessage.of(JopMessageType.ACK, 0));
			feedbackOut.write(frame);
		}
		feedbackOut.flush();

		// Should receive all 3
		for (int i = 0; i < 3; i++) {
			JopMessage resp = transport.receive(2000);
			assertEquals(JopMessageType.ACK, resp.type());
		}
	}

	@Test
	public void testReceiveRegistersResponse() throws Exception {
		byte[] payload = new byte[48]; // 12 registers
		JopProtocolCodec.putInt32(payload, 0, 42);   // PC = 42
		JopProtocolCodec.putInt32(payload, 4, 10);   // JPC = 10
		JopProtocolCodec.putInt32(payload, 8, 100);  // A = 100
		JopProtocolCodec.putInt32(payload, 16, 64);  // SP = 64

		JopMessage regMsg = JopMessage.of(JopMessageType.REGISTERS, 0, payload);
		feedbackOut.write(JopProtocolCodec.encode(regMsg));
		feedbackOut.flush();

		JopMessage received = transport.receive(2000);
		assertEquals(JopMessageType.REGISTERS, received.type());
		int[] regs = JopProtocolCodec.parseRegisters(received.payload());
		assertEquals(12, regs.length);
		assertEquals(42, regs[0]);  // PC
		assertEquals(10, regs[1]);  // JPC
		assertEquals(100, regs[2]); // A
		assertEquals(64, regs[4]);  // SP
	}
}
