package com.jopdesign.microcode;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Plugin for the JOP microcode assembly editor.
 */
public class MicrocodePlugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "com.jopdesign.microcode";

	private static MicrocodePlugin instance;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		instance = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		instance = null;
		super.stop(context);
	}

	public static MicrocodePlugin getDefault() {
		return instance;
	}
}
