package com.jopdesign.ui;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * UI plugin for JOP Eclipse tooling. Provides perspective, property pages,
 * and actions for managing JOP projects.
 */
public class JopUIPlugin extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "com.jopdesign.ui";

	private static JopUIPlugin instance;

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

	public static JopUIPlugin getDefault() {
		return instance;
	}
}
