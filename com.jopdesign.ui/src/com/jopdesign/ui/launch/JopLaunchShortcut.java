package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

import com.jopdesign.core.JopCorePlugin;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;
import com.jopdesign.ui.JopUIPlugin;

/**
 * Launch shortcut for "Run/Debug As > JOP Application".
 * Handles .jop files (JopSim) and Java compilation units (RTL sim default).
 */
public class JopLaunchShortcut implements ILaunchShortcut {

	private static final String CONFIG_TYPE_ID = "com.jopdesign.ui.launch.jopApplication";

	@Override
	public void launch(ISelection selection, String mode) {
		if (selection instanceof IStructuredSelection structSel) {
			Object element = structSel.getFirstElement();
			if (element instanceof ICompilationUnit cu) {
				launchCompilationUnit(cu, mode);
			} else if (element instanceof IFile file) {
				if ("java".equals(file.getFileExtension())) {
					ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
					if (cu != null) {
						launchCompilationUnit(cu, mode);
						return;
					}
				}
				launch(file, mode);
			}
		}
	}

	@Override
	public void launch(IEditorPart editor, String mode) {
		IEditorInput input = editor.getEditorInput();
		IFile file = input.getAdapter(IFile.class);
		if (file != null) {
			if ("java".equals(file.getFileExtension())) {
				ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
				if (cu != null) {
					launchCompilationUnit(cu, mode);
					return;
				}
			}
			launch(file, mode);
		}
	}

	private void launchCompilationUnit(ICompilationUnit cu, String mode) {
		try {
			String mainClass = extractMainClass(cu);
			IProject project = cu.getJavaProject().getProject();
			ILaunchConfiguration config = findOrCreateJavaConfig(mainClass, project);
			DebugUITools.launch(config, mode);
		} catch (CoreException e) {
			JopUIPlugin.getDefault().getLog().log(
					new Status(IStatus.ERROR, JopUIPlugin.PLUGIN_ID, "Launch failed", e));
		}
	}

	private String extractMainClass(ICompilationUnit cu) throws CoreException {
		IType[] types = cu.getTypes();
		if (types.length > 0) {
			return types[0].getFullyQualifiedName();
		}
		// Fallback: derive from file name
		String name = cu.getElementName();
		if (name.endsWith(".java")) {
			name = name.substring(0, name.length() - 5);
		}
		String pkg = cu.getParent() != null ? cu.getParent().getElementName() : "";
		return pkg.isEmpty() ? name : pkg + "." + name;
	}

	private ILaunchConfiguration findOrCreateJavaConfig(String mainClass, IProject project)
			throws CoreException {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = manager.getLaunchConfigurationType(CONFIG_TYPE_ID);

		// Look for existing config matching this main class
		ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);
		for (ILaunchConfiguration config : configs) {
			if (mainClass.equals(config.getAttribute(JopLaunchDelegate.ATTR_MAIN_CLASS, ""))) {
				return config;
			}
		}

		// Create new config with RTL sim defaults
		String name = manager.generateLaunchConfigurationName(mainClass);
		ILaunchConfigurationWorkingCopy wc = type.newInstance(null, name);

		wc.setAttribute(JopLaunchDelegate.ATTR_TARGET_TYPE, JopLaunchDelegate.TARGET_RTLSIM);
		wc.setAttribute(JopLaunchDelegate.ATTR_MAIN_CLASS, mainClass);

		// Pre-populate SBT project dir from JOP_HOME preference
		String jopHome = JopProjectPreferences.get(project, JopPreferences.JOP_HOME, "");
		if (jopHome.isEmpty()) {
			jopHome = InstanceScope.INSTANCE.getNode(JopCorePlugin.PLUGIN_ID)
					.get(JopPreferences.JOP_HOME, "");
		}
		wc.setAttribute(JopLaunchDelegate.ATTR_SBT_PROJECT_DIR, jopHome);
		wc.setAttribute(JopLaunchDelegate.ATTR_SBT_PATH, "sbt");
		wc.setAttribute(JopLaunchDelegate.ATTR_DEBUG_PORT, 4567);
		wc.setAttribute(JopLaunchDelegate.ATTR_SERIAL_PORT, "/dev/ttyUSB0");
		wc.setAttribute(JopLaunchDelegate.ATTR_BAUD_RATE, 1_000_000);

		return wc.doSave();
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
