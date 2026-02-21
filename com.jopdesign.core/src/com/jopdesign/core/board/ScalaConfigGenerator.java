package com.jopdesign.core.board;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.jopdesign.core.io.PeripheralDefinition;
import com.jopdesign.core.io.PeripheralRegistry;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;

/**
 * Generates SpinalHDL/Scala configuration snippets from the project's
 * board configuration preferences.
 *
 * <p>The output matches the {@code JopConfig} case class constructor
 * in the JOP-SpinalHDL project and can be pasted directly into Scala
 * source or used by the builder for SpinalHDL generation.
 */
public final class ScalaConfigGenerator {

	private ScalaConfigGenerator() {}

	/**
	 * Generate a JopConfig Scala constructor call from project preferences.
	 *
	 * @param project the JOP project to read configuration from
	 * @return Scala source string, e.g. {@code JopConfig(methodCacheSize = 4096, ...)}
	 */
	public static String generate(IProject project) {
		int methodCacheSize = getInt(project, JopPreferences.METHOD_CACHE_SIZE, 4096);
		int stackBufferSize = getInt(project, JopPreferences.STACK_BUFFER_SIZE, 192);
		boolean useOcache = getBool(project, JopPreferences.USE_OCACHE, true);
		int ocacheWayBits = getInt(project, JopPreferences.OCACHE_WAY_BITS, 4);
		boolean useAcache = getBool(project, JopPreferences.USE_ACACHE, true);
		int acacheWayBits = getInt(project, JopPreferences.ACACHE_WAY_BITS, 4);
		boolean enableMultiCore = getBool(project, JopPreferences.ENABLE_MULTI_CORE, false);
		int cpuCount = getInt(project, JopPreferences.CPU_COUNT, 1);
		boolean enableDebug = getBool(project, JopPreferences.ENABLE_DEBUG, false);

		String boardId = JopProjectPreferences.get(project, JopPreferences.BOARD_ID, "");
		BoardDefinition board = boardId.isEmpty() ? null : BoardRegistry.getBoard(boardId);

		StringBuilder sb = new StringBuilder();
		sb.append("JopConfig(\n");
		sb.append("  methodCacheSize = ").append(methodCacheSize).append(",\n");
		sb.append("  stackBufferSize = ").append(stackBufferSize).append(",\n");
		sb.append("  useOcache = ").append(useOcache).append(",\n");
		if (useOcache) {
			sb.append("  ocacheWayBits = ").append(ocacheWayBits).append(",\n");
		}
		sb.append("  useAcache = ").append(useAcache).append(",\n");
		if (useAcache) {
			sb.append("  acacheWayBits = ").append(acacheWayBits).append(",\n");
		}
		if (board != null && !board.fpgaTarget().isEmpty()) {
			String target = board.fpgaTarget().equals("xilinx") ? "Xilinx" : "Altera";
			sb.append("  fpgaTarget = FpgaTarget.").append(target).append(",\n");
		}
		sb.append("  enableCache = ").append(useOcache || useAcache).append(",\n");
		sb.append("  enableMultiCore = ").append(enableMultiCore).append(",\n");
		if (enableMultiCore) {
			sb.append("  cpuCnt = ").append(cpuCount).append(",\n");
		}
		sb.append("  enableDebug = ").append(enableDebug);

		// IO peripherals
		List<String> ioComments = getIoPeripheralComments(project);
		if (!ioComments.isEmpty()) {
			sb.append(",\n");
			sb.append(String.join("\n", ioComments));
		}

		sb.append('\n');
		sb.append(")");
		return sb.toString();
	}

	/**
	 * Generate the SpinalHDL top-level main class invocation command.
	 *
	 * @param project the JOP project
	 * @return the sbt runMain command, or empty string if no board selected
	 */
	public static String generateSbtCommand(IProject project) {
		String boardId = JopProjectPreferences.get(project, JopPreferences.BOARD_ID, "");
		BoardDefinition board = boardId.isEmpty() ? null : BoardRegistry.getBoard(boardId);
		if (board == null || board.topModule().isEmpty()) {
			return "";
		}
		return "sbt \"runMain " + board.topModule() + "\"";
	}

	private static List<String> getIoPeripheralComments(IProject project) {
		List<String> lines = new ArrayList<>();
		String peripherals = JopProjectPreferences.get(project,
				JopPreferences.IO_PERIPHERALS, "");
		if (peripherals.isEmpty()) return lines;

		for (String id : peripherals.split(",")) {
			id = id.trim();
			if (id.isEmpty()) continue;
			PeripheralDefinition p = PeripheralRegistry.getPeripheral(id);
			if (p == null) continue;
			String slotVal = JopProjectPreferences.get(project,
					JopPreferences.IO_SLOT_PREFIX + id, "");
			int slot = p.defaultSlot();
			if (!slotVal.isEmpty()) {
				try {
					slot = Integer.parseInt(slotVal);
				} catch (NumberFormatException e) {
					// use default
				}
			}
			lines.add("  // IO slot " + slot + ": " + p.name()
					+ " (" + p.spinalClassName() + ")");
		}
		return lines;
	}

	private static int getInt(IProject project, String key, int defaultValue) {
		String val = JopProjectPreferences.get(project, key, "");
		if (val.isEmpty()) return defaultValue;
		try {
			return Integer.parseInt(val);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static boolean getBool(IProject project, String key, boolean defaultValue) {
		String val = JopProjectPreferences.get(project, key, "");
		if (val.isEmpty()) return defaultValue;
		return Boolean.parseBoolean(val);
	}
}
