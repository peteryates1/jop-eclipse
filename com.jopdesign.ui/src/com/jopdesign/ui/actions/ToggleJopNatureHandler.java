package com.jopdesign.ui.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import com.jopdesign.core.nature.JopNature;

/**
 * Handler for toggling the JOP nature on a Java project.
 * Available in the project Configure context menu.
 */
public class ToggleJopNatureHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (!(selection instanceof IStructuredSelection structured)) {
			return null;
		}

		for (Object element : structured) {
			IProject project = null;
			if (element instanceof IProject p) {
				project = p;
			} else if (element instanceof IAdaptable adaptable) {
				project = adaptable.getAdapter(IProject.class);
			}

			if (project != null) {
				try {
					toggleNature(project);
				} catch (CoreException e) {
					throw new ExecutionException(
							"Failed to toggle JOP nature on " + project.getName(), e);
				}
			}
		}
		return null;
	}

	private void toggleNature(IProject project) throws CoreException {
		if (JopNature.hasNature(project)) {
			JopNature.removeNature(project);
		} else {
			JopNature.addNature(project);
		}
	}
}
