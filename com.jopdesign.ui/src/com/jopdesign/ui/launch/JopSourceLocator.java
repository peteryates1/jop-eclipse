package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IPersistableSourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

import com.jopdesign.microcode.debug.JopStackFrame;

/**
 * Source locator for the unified JOP debug model.
 * Maps stack frames to the microcode source file or Java source file.
 */
public class JopSourceLocator implements IPersistableSourceLocator {

	private String microcodeFilePath;
	private String mainClassName;

	@Override
	public Object getSourceElement(IStackFrame stackFrame) {
		if (!(stackFrame instanceof JopStackFrame)) {
			return null;
		}

		// Try microcode file first
		if (microcodeFilePath != null && !microcodeFilePath.isEmpty()) {
			IFile file = ResourcesPlugin.getWorkspace().getRoot()
					.getFile(new Path(microcodeFilePath));
			if (file.exists()) {
				return file;
			}
		}

		// Fall back to Java source file from main class
		if (mainClassName != null && !mainClassName.isEmpty()) {
			return findJavaSource(mainClassName);
		}

		return null;
	}

	private Object findJavaSource(String className) {
		SearchPattern pattern = SearchPattern.createPattern(
				className, IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
		if (pattern == null) {
			return null;
		}

		Object[] result = new Object[1];
		SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) {
				if (result[0] != null) return;
				Object element = match.getElement();
				if (element instanceof IType type) {
					try {
						var cu = type.getCompilationUnit();
						if (cu != null) {
							result[0] = cu.getCorrespondingResource();
						}
					} catch (JavaModelException e) {
						// Ignore
					}
				}
			}
		};

		try {
			new SearchEngine().search(
					pattern,
					new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(),
					requestor, null);
		} catch (CoreException e) {
			// Ignore
		}

		return result[0];
	}

	@Override
	public void initializeFromMemento(String memento) throws CoreException {
		// Format: "microcodeFile|mainClass"
		if (memento != null) {
			int sep = memento.indexOf('|');
			if (sep >= 0) {
				this.microcodeFilePath = memento.substring(0, sep);
				this.mainClassName = memento.substring(sep + 1);
			} else {
				this.microcodeFilePath = memento;
			}
		}
	}

	@Override
	public String getMemento() throws CoreException {
		String mc = microcodeFilePath != null ? microcodeFilePath : "";
		String mn = mainClassName != null ? mainClassName : "";
		return mc + "|" + mn;
	}

	@Override
	public void initializeDefaults(ILaunchConfiguration configuration) throws CoreException {
		this.microcodeFilePath = configuration.getAttribute(
				JopLaunchDelegate.ATTR_MICROCODE_FILE, "");
		this.mainClassName = configuration.getAttribute(
				JopLaunchDelegate.ATTR_MAIN_CLASS, "");
	}
}
