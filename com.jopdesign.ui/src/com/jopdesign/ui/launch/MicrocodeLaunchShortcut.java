package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

/**
 * Launch shortcut for "Debug As > JOP Microcode Debug".
 * Creates or reuses a launch configuration for the selected .asm/.mic file.
 */
public class MicrocodeLaunchShortcut implements ILaunchShortcut {

	private static final String CONFIG_TYPE_ID = "com.jopdesign.ui.launch.microcodeDebug";

	@Override
	public void launch(ISelection selection, String mode) {
		if (selection instanceof IStructuredSelection structSel) {
			Object element = structSel.getFirstElement();
			if (element instanceof IFile file) {
				launch(file, mode);
			}
		}
	}

	@Override
	public void launch(IEditorPart editor, String mode) {
		IEditorInput input = editor.getEditorInput();
		IFile file = input.getAdapter(IFile.class);
		if (file != null) {
			launch(file, mode);
		}
	}

	private void launch(IFile file, String mode) {
		try {
			ILaunchConfiguration config = findOrCreateConfig(file);
			DebugUITools.launch(config, mode);
		} catch (CoreException e) {
			// Log error
			e.printStackTrace();
		}
	}

	private ILaunchConfiguration findOrCreateConfig(IFile file) throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(CONFIG_TYPE_ID);
		String filePath = file.getFullPath().toString();

		// Look for existing config for this file
		ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);
		for (ILaunchConfiguration config : configs) {
			if (filePath.equals(config.getAttribute(MicrocodeLaunchDelegate.ATTR_MICROCODE_FILE, ""))) {
				return config;
			}
		}

		// Create new config
		String name = manager.generateLaunchConfigurationName(file.getName());
		ILaunchConfigurationWorkingCopy wc = type.newInstance(null, name);
		wc.setAttribute(MicrocodeLaunchDelegate.ATTR_MICROCODE_FILE, filePath);
		wc.setAttribute(MicrocodeLaunchDelegate.ATTR_INITIAL_SP, 64);
		wc.setAttribute(MicrocodeLaunchDelegate.ATTR_MEM_SIZE, 1024);
		return wc.doSave();
	}
}
