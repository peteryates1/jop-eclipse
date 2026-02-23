package com.jopdesign.ui.launch;

import org.eclipse.core.resources.IFile;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.IValueDetailListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.part.FileEditorInput;

import com.jopdesign.microcode.debug.MicrocodeDebugTarget;
import com.jopdesign.microcode.debug.MicrocodeLineBreakpoint;
import com.jopdesign.microcode.debug.MicrocodeStackFrame;
import com.jopdesign.microcode.debug.MicrocodeThread;
import com.jopdesign.microcode.editor.MicrocodeEditor;

/**
 * Provides labels and editor associations for the microcode debug model.
 */
public class MicrocodeDebugModelPresentation extends LabelProvider implements IDebugModelPresentation {

	@Override
	public String getText(Object element) {
		if (element instanceof MicrocodeDebugTarget target) {
			return target.getName();
		}
		if (element instanceof MicrocodeThread thread) {
			return thread.getName();
		}
		if (element instanceof MicrocodeStackFrame frame) {
			return frame.getName();
		}
		if (element instanceof MicrocodeLineBreakpoint bp) {
			try {
				return "Microcode Breakpoint [line: " + bp.getLineNumber() + "]";
			} catch (Exception e) {
				return "Microcode Breakpoint";
			}
		}
		return super.getText(element);
	}

	@Override
	public Image getImage(Object element) {
		return null; // Use default debug icons
	}

	@Override
	public void computeDetail(IValue value, IValueDetailListener listener) {
		try {
			listener.detailComputed(value, value.getValueString());
		} catch (Exception e) {
			listener.detailComputed(value, "?");
		}
	}

	@Override
	public void setAttribute(String attribute, Object value) {
		// No configurable attributes
	}

	@Override
	public String getEditorId(IEditorInput input, Object element) {
		if (element instanceof IFile file) {
			String ext = file.getFileExtension();
			if ("asm".equals(ext) || "mic".equals(ext)) {
				return MicrocodeEditor.EDITOR_ID;
			}
		}
		if (element instanceof ILineBreakpoint) {
			return MicrocodeEditor.EDITOR_ID;
		}
		return null;
	}

	@Override
	public IEditorInput getEditorInput(Object element) {
		if (element instanceof IFile file) {
			return new FileEditorInput(file);
		}
		if (element instanceof ILineBreakpoint bp) {
			if (bp.getMarker().getResource() instanceof IFile file) {
				return new FileEditorInput(file);
			}
		}
		return null;
	}
}
