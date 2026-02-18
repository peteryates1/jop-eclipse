package com.jopdesign.ui.properties;

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.ui.JopUIPlugin;

/**
 * Workspace-level preference page for JOP settings.
 * Accessible via Window > Preferences > JOP.
 */
public class JopPreferencePage extends FieldEditorPreferencePage
		implements IWorkbenchPreferencePage {

	public JopPreferencePage() {
		super(GRID);
		setDescription("Global settings for JOP (Java Optimized Processor) development.");
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(JopUIPlugin.getDefault().getPreferenceStore());
	}

	@Override
	protected void createFieldEditors() {
		addField(new DirectoryFieldEditor(
				JopPreferences.JOP_HOME,
				"JOP Home:",
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				JopPreferences.SERIAL_PORT,
				"Default Serial Port:",
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				JopPreferences.BOARD_TARGET,
				"Default Board Target:",
				getFieldEditorParent()));
	}
}
