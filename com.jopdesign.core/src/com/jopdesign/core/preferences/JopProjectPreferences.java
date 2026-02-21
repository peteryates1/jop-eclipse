package com.jopdesign.core.preferences;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.jopdesign.core.JopCorePlugin;

/**
 * Utility for accessing JOP preferences with project-scope-first lookup.
 * Checks the project-scoped node first, then falls back to workspace scope.
 */
public final class JopProjectPreferences {

	private JopProjectPreferences() {}

	/**
	 * Get the project-scoped preference node for JOP settings.
	 */
	public static IEclipsePreferences forProject(IProject project) {
		return new ProjectScope(project).getNode(JopCorePlugin.PLUGIN_ID);
	}

	/**
	 * Get a preference value, checking project scope first, then workspace scope.
	 */
	public static String get(IProject project, String key, String defaultValue) {
		IEclipsePreferences projectPrefs = forProject(project);
		String value = projectPrefs.get(key, null);
		if (value != null && !value.isEmpty()) {
			return value;
		}
		IEclipsePreferences workspacePrefs = InstanceScope.INSTANCE.getNode(JopCorePlugin.PLUGIN_ID);
		return workspacePrefs.get(key, defaultValue);
	}
}
