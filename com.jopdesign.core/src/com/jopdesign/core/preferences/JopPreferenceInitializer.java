package com.jopdesign.core.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import com.jopdesign.core.JopCorePlugin;

/**
 * Initializes default preference values for JOP tooling.
 */
public class JopPreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(JopCorePlugin.PLUGIN_ID);

		// Installation
		defaults.put(JopPreferences.JOP_HOME, "");

		// Serial
		defaults.put(JopPreferences.SERIAL_PORT, "/dev/ttyUSB0");
		defaults.putInt(JopPreferences.SERIAL_BAUD, 1000000);

		// Board configuration
		defaults.put(JopPreferences.BOARD_ID, "qmtech-ep4cgx150-bram");
		defaults.put(JopPreferences.BOARD_TARGET, "qmtech-ep4cgx150-bram");
		defaults.put(JopPreferences.BOOT_MODE, "bram");
		defaults.put(JopPreferences.MEMORY_TYPE, "bram");

		// JopConfig hardware parameters
		defaults.putInt(JopPreferences.METHOD_CACHE_SIZE, 4096);
		defaults.putInt(JopPreferences.STACK_BUFFER_SIZE, 64);
		defaults.putBoolean(JopPreferences.USE_OCACHE, true);
		defaults.putInt(JopPreferences.OCACHE_WAY_BITS, 4);
		defaults.putBoolean(JopPreferences.USE_ACACHE, true);
		defaults.putInt(JopPreferences.ACACHE_WAY_BITS, 4);

		// Multi-Core / CMP
		defaults.putBoolean(JopPreferences.ENABLE_MULTI_CORE, false);
		defaults.putInt(JopPreferences.CPU_COUNT, 1);
		defaults.put(JopPreferences.ARBITER_TYPE, "tdma");
		defaults.putBoolean(JopPreferences.ENABLE_DEBUG, false);

		// FPGA tool paths
		defaults.put(JopPreferences.SBT_PATH, "sbt");
		defaults.put(JopPreferences.QUARTUS_PATH, "");
		defaults.put(JopPreferences.VIVADO_PATH, "");

		// Build
		defaults.put(JopPreferences.QUARTUS_PROJECT, "");
		defaults.putBoolean(JopPreferences.USE_SIMULATOR, true);
		defaults.put(JopPreferences.MAIN_CLASS, "");
		defaults.put(JopPreferences.JOP_OUTPUT_DIR, "build");
		defaults.put(JopPreferences.MICROCODE_DEFINES, "SERIAL");
	}
}
