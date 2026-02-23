package com.jopdesign.core.sim.protocol;

/**
 * Exception thrown for debug protocol framing, CRC, or timeout errors.
 */
public class JopProtocolException extends Exception {

	private static final long serialVersionUID = 1L;

	public JopProtocolException(String message) {
		super(message);
	}

	public JopProtocolException(String message, Throwable cause) {
		super(message, cause);
	}
}
