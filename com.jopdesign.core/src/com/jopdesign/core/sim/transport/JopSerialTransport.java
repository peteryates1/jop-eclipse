package com.jopdesign.core.sim.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jopdesign.core.sim.protocol.JopMessage;
import com.jopdesign.core.sim.protocol.JopMessageType;
import com.jopdesign.core.sim.protocol.JopProtocolCodec;
import com.jopdesign.core.sim.protocol.JopProtocolException;

/**
 * Serial port transport for the JOP debug protocol.
 * Used for FPGA hardware debug via USB-serial.
 *
 * <p>This implementation uses plain java.io streams so it can work with
 * any serial library (jSerialComm, RXTX, etc.) that provides standard
 * InputStream/OutputStream. The caller is responsible for opening the
 * serial port and passing the streams.
 *
 * <p>Default configuration: 1Mbaud, 8N1, RTS/CTS flow control.
 */
public class JopSerialTransport implements JopTransport {

	private final String portName;
	private final int baudRate;

	private InputStream inputStream;
	private OutputStream outputStream;
	private AutoCloseable portHandle;
	private Thread readThread;
	private volatile boolean connected;
	private volatile NotificationListener notificationListener;

	private final BlockingQueue<JopMessage> responseQueue = new LinkedBlockingQueue<>();
	private final BlockingQueue<JopProtocolException> errorQueue = new LinkedBlockingQueue<>();

	/**
	 * Create a serial transport for the given port.
	 *
	 * @param portName serial port path (e.g., "/dev/ttyUSB0" or "COM3")
	 * @param baudRate baud rate (default: 1000000)
	 */
	public JopSerialTransport(String portName, int baudRate) {
		this.portName = portName;
		this.baudRate = baudRate;
	}

	/** Create with default 1Mbaud. */
	public JopSerialTransport(String portName) {
		this(portName, 1_000_000);
	}

	/**
	 * Open with externally-provided streams (for testing or when the caller
	 * manages the serial port lifecycle).
	 *
	 * @param in          input stream from serial port
	 * @param out         output stream to serial port
	 * @param portHandle  closeable handle (port object), or null
	 */
	public void open(InputStream in, OutputStream out, AutoCloseable portHandle) {
		this.inputStream = in;
		this.outputStream = out;
		this.portHandle = portHandle;
		this.connected = true;
		startReadThread();
	}

	@Override
	public void open() throws JopProtocolException {
		// Try to open via jSerialComm if available at runtime
		try {
			Class<?> spClass = Class.forName("com.fazecast.jSerialComm.SerialPort");
			Object port = spClass.getMethod("getCommPort", String.class).invoke(null, portName);
			spClass.getMethod("setBaudRate", int.class).invoke(port, baudRate);
			spClass.getMethod("setNumDataBits", int.class).invoke(port, 8);
			spClass.getMethod("setNumStopBits", int.class).invoke(port, 1);
			// Set flow control: RTS_CTS = 16 in jSerialComm
			spClass.getMethod("setFlowControl", int.class).invoke(port, 16);
			boolean opened = (boolean) spClass.getMethod("openPort").invoke(port);
			if (!opened) {
				throw new JopProtocolException("Failed to open serial port: " + portName);
			}
			InputStream in = (InputStream) spClass.getMethod("getInputStream").invoke(port);
			OutputStream out = (OutputStream) spClass.getMethod("getOutputStream").invoke(port);
			open(in, out, (AutoCloseable) () -> {
				spClass.getMethod("closePort").invoke(port);
			});
		} catch (ClassNotFoundException e) {
			throw new JopProtocolException(
					"jSerialComm library not found. Add jSerialComm to the classpath for serial port support.");
		} catch (JopProtocolException e) {
			throw e;
		} catch (Exception e) {
			throw new JopProtocolException("Failed to open serial port " + portName + ": " + e.getMessage(), e);
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
		JopProtocolException error = errorQueue.poll();
		if (error != null) {
			throw error;
		}

		try {
			JopMessage msg = responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
			if (msg == null) {
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
		if (portHandle != null) {
			try {
				portHandle.close();
			} catch (Exception e) {
				// Ignore
			}
			portHandle = null;
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

	public String getPortName() {
		return portName;
	}

	public int getBaudRate() {
		return baudRate;
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
		}, "JopSerial-Reader-" + portName);
		readThread.setDaemon(true);
		readThread.start();
	}
}
