package com.jopdesign.core;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Core plugin for JOP (Java Optimized Processor) Eclipse tooling.
 * Provides nature, builder, and toolchain abstractions with no UI dependencies.
 */
public class JopCorePlugin extends Plugin {

	public static final String PLUGIN_ID = "com.jopdesign.core";

	private static JopCorePlugin instance;

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

	public static JopCorePlugin getDefault() {
		return instance;
	}
}
