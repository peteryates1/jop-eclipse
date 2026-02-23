package com.jopdesign.core.sim;

import com.jopdesign.core.sim.transport.JopSerialTransport;

/**
 * IJopTarget that debugs a JOP processor running on FPGA hardware
 * via USB-serial connection.
 *
 * <p>Unlike {@link RtlSimJopTarget}, this does not launch any process —
 * it connects directly to an existing serial port where the FPGA's
 * debug interface is attached.
 */
public class FpgaJopTarget extends ProtocolJopTarget {

	private final String serialPort;
	private final int baudRate;

	/**
	 * @param serialPort  serial port path (e.g., "/dev/ttyUSB0" or "COM3")
	 * @param baudRate    baud rate (default 1000000)
	 */
	public FpgaJopTarget(String serialPort, int baudRate) {
		super("FPGA Hardware [" + serialPort + "]", "fpga",
				new JopSerialTransport(serialPort, baudRate), 0);
		this.serialPort = serialPort;
		this.baudRate = baudRate;
	}

	/** Create with default 1Mbaud. */
	public FpgaJopTarget(String serialPort) {
		this(serialPort, 1_000_000);
	}

	public String getSerialPort() {
		return serialPort;
	}

	public int getBaudRate() {
		return baudRate;
	}
}
