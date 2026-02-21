package com.jopdesign.core.sim;

/**
 * Exception thrown by JOP target operations.
 */
public class JopTargetException extends Exception {

	private static final long serialVersionUID = 1L;

	public JopTargetException(String message) {
		super(message);
	}

	public JopTargetException(String message, Throwable cause) {
		super(message, cause);
	}
}
