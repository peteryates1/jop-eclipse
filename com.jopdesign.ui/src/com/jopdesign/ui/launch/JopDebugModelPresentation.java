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

import com.jopdesign.microcode.debug.JopDebugTarget;
import com.jopdesign.microcode.debug.JopStackFrame;
import com.jopdesign.microcode.debug.JopThread;
import com.jopdesign.microcode.debug.MicrocodeLineBreakpoint;
import com.jopdesign.microcode.editor.MicrocodeEditor;

/**
 * Provides labels and editor associations for the unified JOP debug model.
 */
public class JopDebugModelPresentation extends LabelProvider implements IDebugModelPresentation {

	@Override
	public String getText(Object element) {
		if (element instanceof JopDebugTarget target) {
			return target.getName();
		}
		if (element instanceof JopThread thread) {
			return thread.getName();
		}
		if (element instanceof JopStackFrame frame) {
			return frame.getName();
		}
		if (element instanceof MicrocodeLineBreakpoint bp) {
			try {
				return "JOP Breakpoint [line: " + bp.getLineNumber() + "]";
			} catch (Exception e) {
				return "JOP Breakpoint";
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
			IFile file = (IFile) bp.getMarker().getResource().getAdapter(IFile.class);
			if (file != null) {
				return new FileEditorInput(file);
			}
		}
		return null;
	}
}
