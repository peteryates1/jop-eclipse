package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IPersistableSourceLocator;
import org.eclipse.debug.core.model.IStackFrame;

import com.jopdesign.microcode.debug.JopStackFrame;

/**
 * Source locator for the unified JOP debug model.
 * Maps stack frames to the microcode source file being debugged.
 */
public class JopSourceLocator implements IPersistableSourceLocator {

	private String microcodeFilePath;

	@Override
	public Object getSourceElement(IStackFrame stackFrame) {
		if (stackFrame instanceof JopStackFrame && microcodeFilePath != null) {
			IFile file = ResourcesPlugin.getWorkspace().getRoot()
					.getFile(new Path(microcodeFilePath));
			if (file.exists()) {
				return file;
			}
		}
		return null;
	}

	@Override
	public void initializeFromMemento(String memento) throws CoreException {
		this.microcodeFilePath = memento;
	}

	@Override
	public String getMemento() throws CoreException {
		return microcodeFilePath;
	}

	@Override
	public void initializeDefaults(ILaunchConfiguration configuration) throws CoreException {
		this.microcodeFilePath = configuration.getAttribute(
				JopLaunchDelegate.ATTR_MICROCODE_FILE, "");
	}
}
