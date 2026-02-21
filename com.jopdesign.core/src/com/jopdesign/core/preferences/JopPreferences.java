package com.jopdesign.core.preferences;

/**
 * Preference constants for JOP tooling configuration.
 */
public final class JopPreferences {

	private JopPreferences() {}

	// ---- Installation ----

	/** Path to JOP installation directory (contains asm/, java/, fpga/) */
	public static final String JOP_HOME = "jop.home";

	// ---- Serial ----

	/** Serial port device path (e.g., /dev/ttyUSB0) */
	public static final String SERIAL_PORT = "jop.serial.port";

	/** Serial baud rate (default 1000000 for 1 Mbaud) */
	public static final String SERIAL_BAUD = "jop.serial.baud";

	// ---- Board configuration ----

	/** Selected board ID (e.g., "qmtech-ep4cgx150-bram") */
	public static final String BOARD_ID = "jop.board.id";

	/** FPGA board target — legacy alias for BOARD_ID */
	public static final String BOARD_TARGET = "jop.board.target";

	/** Boot mode: "bram" or "serial" */
	public static final String BOOT_MODE = "jop.boot.mode";

	/** Memory type: "bram", "sdram", or "ddr3" */
	public static final String MEMORY_TYPE = "jop.board.memory.type";

	// ---- JopConfig hardware parameters ----

	/** Method cache size in bytes (256-16384) */
	public static final String METHOD_CACHE_SIZE = "jop.board.method.cache.size";

	/** On-chip stack buffer size in words (8-256) */
	public static final String STACK_BUFFER_SIZE = "jop.board.stack.buffer.size";

	/** Enable object field cache */
	public static final String USE_OCACHE = "jop.board.use.ocache";

	/** Object cache associativity: 2^n ways (2-4) */
	public static final String OCACHE_WAY_BITS = "jop.board.ocache.way.bits";

	/** Enable array element cache */
	public static final String USE_ACACHE = "jop.board.use.acache";

	/** Array cache associativity: 2^n ways (2-4) */
	public static final String ACACHE_WAY_BITS = "jop.board.acache.way.bits";

	// ---- Multi-Core / CMP ----

	/** Enable multi-core CMP support */
	public static final String ENABLE_MULTI_CORE = "jop.board.enable.multicore";

	/** Number of CPU cores (1-8) */
	public static final String CPU_COUNT = "jop.board.cpu.count";

	/** Memory arbiter type: "tdma", "priority", or "roundrobin" */
	public static final String ARBITER_TYPE = "jop.board.arbiter.type";

	/** Enable debug instrumentation in hardware */
	public static final String ENABLE_DEBUG = "jop.board.enable.debug";

	// ---- FPGA tool paths ----

	/** Path to SBT executable (for SpinalHDL Verilog generation) */
	public static final String SBT_PATH = "jop.sbt.path";

	/** Path to Quartus bin directory (e.g., /opt/altera/25.1/quartus/bin) */
	public static final String QUARTUS_PATH = "jop.quartus.path";

	/** Path to Vivado installation (e.g., /opt/xilinx/2025.2/Vivado) */
	public static final String VIVADO_PATH = "jop.vivado.path";

	// ---- IO Peripherals ----

	/** Comma-separated list of enabled peripheral IDs (e.g., "gpio,spi") */
	public static final String IO_PERIPHERALS = "jop.board.io.peripherals";

	/**
	 * Slot assignment for a peripheral. Key format: jop.board.io.slot.{peripheralId}
	 * Value: slot number (2 or 3).
	 */
	public static final String IO_SLOT_PREFIX = "jop.board.io.slot.";

	// ---- Build ----

	/** Path to Quartus project file (.qpf) */
	public static final String QUARTUS_PROJECT = "jop.quartus.project";

	/** Whether to use JopSim simulation by default */
	public static final String USE_SIMULATOR = "jop.use.simulator";

	/** Main class for JOP application */
	public static final String MAIN_CLASS = "jop.main.class";

	/** Output directory for .jop files */
	public static final String JOP_OUTPUT_DIR = "jop.output.dir";

	/** Comma-separated preprocessor defines for microcode build (e.g., SERIAL,SIMULATION) */
	public static final String MICROCODE_DEFINES = "jop.microcode.defines";
}
