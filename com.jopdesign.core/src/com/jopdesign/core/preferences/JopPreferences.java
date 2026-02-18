package com.jopdesign.core.preferences;

/**
 * Preference constants for JOP tooling configuration.
 */
public final class JopPreferences {

	private JopPreferences() {}

	/** Path to JOP installation directory (contains asm/, java/, fpga/) */
	public static final String JOP_HOME = "jop.home";

	/** Serial port device path (e.g., /dev/ttyUSB0) */
	public static final String SERIAL_PORT = "jop.serial.port";

	/** Serial baud rate (default 1000000 for 1 Mbaud) */
	public static final String SERIAL_BAUD = "jop.serial.baud";

	/** FPGA board target (e.g., qmtech-ep4cgx150-bram) */
	public static final String BOARD_TARGET = "jop.board.target";

	/** Boot mode: bram or sdram */
	public static final String BOOT_MODE = "jop.boot.mode";

	/** Path to Quartus project file (.qpf) */
	public static final String QUARTUS_PROJECT = "jop.quartus.project";

	/** Whether to use JopSim simulation by default */
	public static final String USE_SIMULATOR = "jop.use.simulator";

	/** Main class for JOP application */
	public static final String MAIN_CLASS = "jop.main.class";

	/** Output directory for .jop files */
	public static final String JOP_OUTPUT_DIR = "jop.output.dir";
}
