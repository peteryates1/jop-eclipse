package com.jopdesign.core.sim.transport;

import com.jopdesign.core.sim.protocol.JopMessage;
import com.jopdesign.core.sim.protocol.JopProtocolException;

/**
 * Abstract byte-stream transport for the JOP debug protocol.
 * Implementations provide the physical connection (TCP socket, serial port)
 * while this interface handles message framing via the protocol codec.
 */
public interface JopTransport extends AutoCloseable {

	/**
	 * Open the transport connection.
	 *
	 * @throws JopProtocolException on connection failure
	 */
	void open() throws JopProtocolException;

	/**
	 * Send a framed protocol message.
	 *
	 * @param message the message to send
	 * @throws JopProtocolException on I/O or framing error
	 */
	void send(JopMessage message) throws JopProtocolException;

	/**
	 * Receive a framed protocol message (blocking with timeout).
	 *
	 * @param timeoutMs timeout in milliseconds
	 * @return the decoded message
	 * @throws JopProtocolException on timeout, I/O, CRC, or framing error
	 */
	JopMessage receive(long timeoutMs) throws JopProtocolException;

	/**
	 * Check if a message is available without blocking.
	 *
	 * @return true if a message can be received without waiting
	 */
	boolean hasMessage();

	/**
	 * Check if the transport is open and connected.
	 */
	boolean isConnected();

	/**
	 * Close the transport connection.
	 */
	@Override
	void close();

	/**
	 * Set a listener for asynchronous notifications (HALTED).
	 * The listener is called from the transport's read thread.
	 *
	 * @param listener the notification listener, or null to remove
	 */
	void setNotificationListener(NotificationListener listener);

	/**
	 * Listener for asynchronous protocol notifications.
	 */
	@FunctionalInterface
	interface NotificationListener {
		void onNotification(JopMessage notification);
	}
}
