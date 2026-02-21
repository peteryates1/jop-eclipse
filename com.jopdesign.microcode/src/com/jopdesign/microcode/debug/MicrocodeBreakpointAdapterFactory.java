package com.jopdesign.microcode.debug;

import java.util.Set;

import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTargetFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPart;

import com.jopdesign.microcode.editor.MicrocodeEditor;

/**
 * Factory that creates {@link MicrocodeBreakpointAdapter} instances
 * for the microcode editor.
 */
public class MicrocodeBreakpointAdapterFactory implements IToggleBreakpointsTargetFactory {

	private static final String TOGGLE_TARGET_ID = "com.jopdesign.microcode.toggleBreakpointTarget";

	@Override
	public Set<String> getToggleTargets(IWorkbenchPart part, ISelection selection) {
		if (part instanceof MicrocodeEditor) {
			return Set.of(TOGGLE_TARGET_ID);
		}
		return Set.of();
	}

	@Override
	public String getDefaultToggleTarget(IWorkbenchPart part, ISelection selection) {
		if (part instanceof MicrocodeEditor) {
			return TOGGLE_TARGET_ID;
		}
		return null;
	}

	@Override
	public IToggleBreakpointsTarget createToggleTarget(String targetID) {
		if (TOGGLE_TARGET_ID.equals(targetID)) {
			return new MicrocodeBreakpointAdapter();
		}
		return null;
	}

	@Override
	public String getToggleTargetName(String targetID) {
		return "Microcode Breakpoints";
	}

	@Override
	public String getToggleTargetDescription(String targetID) {
		return "Toggle breakpoints in JOP microcode assembly files";
	}
}
