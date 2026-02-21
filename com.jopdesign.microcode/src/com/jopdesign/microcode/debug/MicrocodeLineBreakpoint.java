package com.jopdesign.microcode.debug;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.LineBreakpoint;

/**
 * A line breakpoint in a microcode assembly file.
 */
public class MicrocodeLineBreakpoint extends LineBreakpoint {

	/** Marker type for microcode breakpoints. */
	public static final String MARKER_TYPE = "com.jopdesign.microcode.microcodeBreakpoint";

	/** Default constructor required for breakpoint restoration. */
	public MicrocodeLineBreakpoint() {
	}

	/**
	 * Create a breakpoint on the given resource at the given line.
	 */
	public MicrocodeLineBreakpoint(IResource resource, int lineNumber) throws CoreException {
		IWorkspaceRunnable runnable = (IProgressMonitor monitor) -> {
			IMarker marker = resource.createMarker(MARKER_TYPE);
			setMarker(marker);
			marker.setAttribute(IBreakpoint.ENABLED, true);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IBreakpoint.ID, getModelIdentifier());
			marker.setAttribute(IMarker.MESSAGE,
					"Microcode Breakpoint: " + resource.getName() + " [line: " + lineNumber + "]");
		};
		run(getMarkerRule(resource), runnable);
	}

	@Override
	public String getModelIdentifier() {
		return MicrocodeDebugTarget.MODEL_ID;
	}
}
