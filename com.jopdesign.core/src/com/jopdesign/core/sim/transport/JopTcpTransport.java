package com.jopdesign.core.sim.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jopdesign.core.sim.protocol.JopMessage;
import com.jopdesign.core.sim.protocol.JopMessageType;
import com.jopdesign.core.sim.protocol.JopProtocolCodec;
import com.jopdesign.core.sim.protocol.JopProtocolException;

/**
 * TCP socket transport for the JOP debug protocol.
 * Used for RTL simulation via JopDebugSim's TCP bridge.
 *
 * <p>The read thread continuously reads framed messages from the socket.
 * Response messages are queued for the caller; asynchronous notifications
 * (HALTED) are dispatched to the notification listener.
 */
public class JopTcpTransport implements JopTransport {

	private final String host;
	private final int port;

	private Socket socket;
	private InputStream inputStream;
	private OutputStream outputStream;
	private Thread readThread;
	private volatile boolean connected;
	private volatile NotificationListener notificationListener;

	private final BlockingQueue<JopMessage> responseQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<JopProtocolException> errorQueue = new LinkedBlockingQueue<>();

	/**
	 * @param host TCP host (typically "localhost")
	 * @param port TCP port (default 4567 for JopDebugSim)
	 */
	public JopTcpTransport(String host, int port) {
		this.host = host;
		this.port = port;
	}

	@Override
	public void open() throws JopProtocolException {
		try {
			socket = new Socket(host, port);
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(0); // Non-blocking reads handled by read thread
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
			connected = true;
			startReadThread();
		} catch (IOException e) {
			throw new JopProtocolException("Failed to connect to " + host + ":" + port, e);
		}
	}

	@Override
	public void send(JopMessage message) throws JopProtocolException {
		if (!connected) {
			throw new JopProtocolException("Transport not connected");
		}
		byte[] frame = JopProtocolCodec.encode(message);
		try {
			synchronized (outputStream) {
				outputStream.write(frame);
				outputStream.flush();
			}
		} catch (IOException e) {
			connected = false;
			throw new JopProtocolException("Failed to send message: " + e.getMessage(), e);
		}
	}

	@Override
	public JopMessage receive(long timeoutMs) throws JopProtocolException {
		// Check for errors from read thread
		JopProtocolException error = errorQueue.poll();
		if (error != null) {
			throw error;
		}

		try {
			JopMessage msg = responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
			if (msg == null) {
				// Check for errors again
				error = errorQueue.poll();
				if (error != null) {
					throw error;
				}
				throw new JopProtocolException("Timeout waiting for response (%d ms)".formatted(timeoutMs));
			}
			return msg;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new JopProtocolException("Interrupted while waiting for response");
		}
	}

	@Override
	public boolean hasMessage() {
		return !responseQueue.isEmpty();
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public void close() {
		connected = false;
		if (readThread != null) {
			readThread.interrupt();
			readThread = null;
		}
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore
			}
			socket = null;
		}
		inputStream = null;
		outputStream = null;
		responseQueue.clear();
		errorQueue.clear();
	}

	@Override
	public void setNotificationListener(NotificationListener listener) {
		this.notificationListener = listener;
	}

	private void startReadThread() {
		readThread = new Thread(() -> {
			while (connected && !Thread.currentThread().isInterrupted()) {
				try {
					JopMessage msg = JopProtocolCodec.decode(inputStream, 60_000);
					if (JopMessageType.isNotification(msg.type())) {
						NotificationListener listener = notificationListener;
						if (listener != null) {
							listener.onNotification(msg);
						}
					} else {
						responseQueue.offer(msg);
					}
				} catch (JopProtocolException e) {
					if (connected) {
						errorQueue.offer(e);
					}
				} catch (IOException e) {
					if (connected) {
						connected = false;
						errorQueue.offer(new JopProtocolException("Connection lost: " + e.getMessage(), e));
					}
				}
			}
		}, "JopTcp-Reader-" + host + ":" + port);
		readThread.setDaemon(true);
		readThread.start();
	}
}
