package com.jopdesign.microcode.debug;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Handles toggling breakpoints in microcode assembly files.
 * Registered as a toggleBreakpointsTargetFactory in plugin.xml.
 */
public class MicrocodeBreakpointAdapter implements IToggleBreakpointsTarget {

	@Override
	public void toggleLineBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
		if (!(part instanceof ITextEditor editor)) return;
		if (!(selection instanceof ITextSelection textSel)) return;

		IEditorInput input = editor.getEditorInput();
		IResource resource = input.getAdapter(IResource.class);
		if (resource == null) return;

		int lineNumber = textSel.getStartLine() + 1; // 1-based

		// Check if a breakpoint already exists at this line
		IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager()
				.getBreakpoints(MicrocodeDebugTarget.MODEL_ID);
		for (IBreakpoint bp : breakpoints) {
			if (bp instanceof ILineBreakpoint lineBp) {
				if (resource.equals(bp.getMarker().getResource())
						&& lineBp.getLineNumber() == lineNumber) {
					// Remove existing breakpoint
					bp.delete();
					return;
				}
			}
		}

		// Add new breakpoint
		new MicrocodeLineBreakpoint(resource, lineNumber);
	}

	@Override
	public boolean canToggleLineBreakpoints(IWorkbenchPart part, ISelection selection) {
		return part instanceof ITextEditor;
	}

	@Override
	public void toggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) {
		// Not supported
	}

	@Override
	public boolean canToggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) {
		return false;
	}

	@Override
	public void toggleWatchpoints(IWorkbenchPart part, ISelection selection) {
		// Not supported
	}

	@Override
	public boolean canToggleWatchpoints(IWorkbenchPart part, ISelection selection) {
		return false;
	}
}
