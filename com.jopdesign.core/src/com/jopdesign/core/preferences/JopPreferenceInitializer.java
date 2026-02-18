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
		defaults.put(JopPreferences.JOP_HOME, "");
		defaults.put(JopPreferences.SERIAL_PORT, "/dev/ttyUSB0");
		defaults.putInt(JopPreferences.SERIAL_BAUD, 1000000);
		defaults.put(JopPreferences.BOARD_TARGET, "qmtech-ep4cgx150-bram");
		defaults.put(JopPreferences.BOOT_MODE, "bram");
		defaults.put(JopPreferences.QUARTUS_PROJECT, "");
		defaults.putBoolean(JopPreferences.USE_SIMULATOR, true);
		defaults.put(JopPreferences.MAIN_CLASS, "");
		defaults.put(JopPreferences.JOP_OUTPUT_DIR, "build");
	}
}
