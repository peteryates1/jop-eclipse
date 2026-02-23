package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
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

import com.jopdesign.ui.JopUIPlugin;

/**
 * Launch shortcut for "Run/Debug As > JOP Application".
 * Defaults to the simulator target.
 */
public class JopLaunchShortcut implements ILaunchShortcut {

	private static final String CONFIG_TYPE_ID = "com.jopdesign.ui.launch.jopApplication";

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
			JopUIPlugin.getDefault().getLog().log(
					new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID, "Launch failed", e));
		}
	}

	private ILaunchConfiguration findOrCreateConfig(IFile file) throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(CONFIG_TYPE_ID);
		String filePath = file.getFullPath().toString();
		boolean isJopFile = "jop".equals(file.getFileExtension());

		// Look for existing config for this file
		String matchAttr = isJopFile ? JopLaunchDelegate.ATTR_JOP_FILE : JopLaunchDelegate.ATTR_MICROCODE_FILE;
		ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);
		for (ILaunchConfiguration config : configs) {
			if (filePath.equals(config.getAttribute(matchAttr, ""))) {
				return config;
			}
		}

		// Create new config
		String name = manager.generateLaunchConfigurationName(file.getName());
		ILaunchConfigurationWorkingCopy wc = type.newInstance(null, name);

		if (isJopFile) {
			// JOP bytecode simulator for .jop files
			wc.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_JOPSIM);
			wc.setAttribute(JopLaunchDelegate.ATTR_JOP_FILE, filePath);
			// Look for companion .link.txt file
			IFile linkFile = file.getProject().getFile(
					file.getProjectRelativePath().toString() + ".link.txt");
			if (linkFile.exists()) {
				wc.setAttribute(JopLaunchDelegate.ATTR_LINK_FILE, linkFile.getFullPath().toString());
			}
		} else {
			// Microcode simulator for .asm/.mic files
			wc.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_SIMULATOR);
			wc.setAttribute(JopLaunchDelegate.ATTR_MICROCODE_FILE, filePath);
			wc.setAttribute(JopLaunchDelegate.ATTR_INITIAL_SP, 64);
			wc.setAttribute(JopLaunchDelegate.ATTR_MEM_SIZE, 1024);
		}
		return wc.doSave();
	}
}
