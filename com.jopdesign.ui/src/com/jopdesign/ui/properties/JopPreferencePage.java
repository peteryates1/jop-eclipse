package com.jopdesign.ui.properties;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.preferences.JopPreferences;

/**
 * Workspace-level preference page for JOP settings.
 * Accessible via Window > Preferences > JOP.
 *
 * Stores preferences in the core plugin's preference node so they are
 * accessible from both UI and headless (builder) contexts.
 */
public class JopPreferencePage extends FieldEditorPreferencePage
		implements IWorkbenchPreferencePage {

	public JopPreferencePage() {
		super(GRID);
		setDescription("Global settings for JOP (Java Optimized Processor) development.");
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE,
				JopCorePlugin.PLUGIN_ID));
	}

	@Override
	protected void createFieldEditors() {
		addField(new DirectoryFieldEditor(
				JopPreferences.JOP_HOME,
				"JOP Home:",
				getFieldEditorParent()));

		addField(new DirectoryFieldEditor(
				JopPreferences.JDK6_HOME,
				"JDK 1.6 Home:",
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				JopPreferences.SERIAL_PORT,
				"Default Serial Port:",
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				JopPreferences.BOARD_TARGET,
				"Default Board Target:",
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				JopPreferences.MICROCODE_DEFINES,
				"Microcode Defines:",
				getFieldEditorParent()));

		// FPGA tool paths
		addField(new StringFieldEditor(
				JopPreferences.SBT_PATH,
				"SBT Path:",
				getFieldEditorParent()));

		addField(new DirectoryFieldEditor(
				JopPreferences.QUARTUS_PATH,
				"Quartus Install Directory:",
				getFieldEditorParent()));

		addField(new DirectoryFieldEditor(
				JopPreferences.VIVADO_PATH,
				"Vivado Install Directory:",
				getFieldEditorParent()));
	}
}
