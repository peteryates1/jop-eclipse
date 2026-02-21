package com.jopdesign.core.board;

/**
 * Immutable definition of a JOP target board, including FPGA details,
 * memory configuration, and default JopConfig hardware parameters.
 *
 * <p>Board definitions are loaded from {@code boards.json} by
 * {@link BoardRegistry} and can be extended by users placing additional
 * JSON files in their project.
 */
public record BoardDefinition(
		// ---- Identity ----
		/** Unique board identifier (e.g., "qmtech-ep4cgx150-bram") */
		String id,
		/** Human-readable board name (e.g., "QMTECH EP4CGX150 (BRAM)") */
		String name,

		// ---- FPGA ----
		/** FPGA family (e.g., "Cyclone IV GX", "Artix-7") */
		String fpgaFamily,
		/** FPGA device identifier (e.g., "EP4CGX150DF27I7", "XC7A35T") */
		String fpgaDevice,
		/** Synthesis tool: "quartus" or "vivado" */
		String synthTool,
		/** FPGA target for SpinalHDL: "altera" or "xilinx" */
		String fpgaTarget,

		// ---- Clocks ----
		/** Board input clock frequency in MHz */
		int clockInputMhz,
		/** System clock frequency in MHz (after PLL) */
		int systemClockMhz,

		// ---- Memory ----
		/** Memory type: "bram", "sdram", or "ddr3" */
		String memoryType,
		/** Memory size description (e.g., "32KB", "32MB", "512MB") */
		String memorySize,
		/** Boot mode: "bram" (embedded) or "serial" (UART download) */
		String bootMode,

		// ---- Serial ----
		/** Default UART baud rate */
		int uartBaud,

		// ---- SpinalHDL ----
		/** SpinalHDL top-level main class (e.g., "jop.system.JopBramTopVerilog") */
		String topModule,
		/** FPGA project directory relative to JOP_HOME (e.g., "fpga/qmtech-ep4cgx150-bram") */
		String fpgaDir,

		// ---- JopConfig hardware parameters ----
		/** Method cache size in bytes (1024-8192) */
		int methodCacheSize,
		/** On-chip stack buffer size in words (16-128) */
		int stackBufferSize,
		/** Enable object field cache */
		boolean useOcache,
		/** Object cache associativity: 2^n ways (2-4) */
		int ocacheWayBits,
		/** Enable array element cache */
		boolean useAcache,
		/** Array cache associativity: 2^n ways (2-4) */
		int acacheWayBits,

		// ---- CMP / Multi-Core ----
		/** Enable multi-core CMP support */
		boolean enableMultiCore,
		/** Number of CPU cores (1-8, only when enableMultiCore is true) */
		int cpuCount,
		/** Enable debug instrumentation */
		boolean enableDebug
) {
	/** Validate parameter ranges */
	public BoardDefinition {
		if (methodCacheSize < 256 || methodCacheSize > 16384) {
			throw new IllegalArgumentException(
					"methodCacheSize must be 256-16384, got " + methodCacheSize);
		}
		if (stackBufferSize < 8 || stackBufferSize > 256) {
			throw new IllegalArgumentException(
					"stackBufferSize must be 8-256, got " + stackBufferSize);
		}
	}
}
