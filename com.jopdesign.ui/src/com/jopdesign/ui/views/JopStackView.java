package com.jopdesign.ui.views;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.contexts.DebugContextEvent;
import org.eclipse.debug.ui.contexts.IDebugContextListener;
import org.eclipse.debug.ui.contexts.IDebugContextService;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;

import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopStackData;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.microcode.debug.JopDebugTarget;
import com.jopdesign.microcode.debug.JopStackFrame;
import com.jopdesign.microcode.debug.JopThread;

/**
 * View showing the JOP stack contents in a table.
 * Annotates special positions (SP, VP, TOS, NOS).
 */
public class JopStackView extends ViewPart implements IDebugContextListener, IDebugEventSetListener {

	public static final String VIEW_ID = "com.jopdesign.ui.views.stackView";

	private TableViewer tableViewer;

	@Override
	public void createPartControl(Composite parent) {
		tableViewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		Table table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		TableViewerColumn indexCol = new TableViewerColumn(tableViewer, SWT.NONE);
		indexCol.getColumn().setText("Index");
		indexCol.getColumn().setWidth(60);
		indexCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return Integer.toString(((StackEntry) element).index);
			}
		});

		TableViewerColumn decCol = new TableViewerColumn(tableViewer, SWT.NONE);
		decCol.getColumn().setText("Value (dec)");
		decCol.getColumn().setWidth(100);
		decCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return Integer.toString(((StackEntry) element).value);
			}
		});

		TableViewerColumn hexCol = new TableViewerColumn(tableViewer, SWT.NONE);
		hexCol.getColumn().setText("Value (hex)");
		hexCol.getColumn().setWidth(100);
		hexCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return String.format("0x%08X", ((StackEntry) element).value);
			}
		});

		TableViewerColumn annotCol = new TableViewerColumn(tableViewer, SWT.NONE);
		annotCol.getColumn().setText("Annotation");
		annotCol.getColumn().setWidth(120);
		annotCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((StackEntry) element).annotation;
			}
		});

		tableViewer.setContentProvider(ArrayContentProvider.getInstance());

		// Register for debug context changes
		IDebugContextService contextService = DebugUITools.getDebugContextManager()
				.getContextService(getSite().getWorkbenchWindow());
		contextService.addDebugContextListener(this);

		// Register for debug events
		DebugPlugin.getDefault().addDebugEventListener(this);

		// Initialize from current context
		updateFromContext(contextService.getActiveContext());
	}

	@Override
	public void debugContextChanged(DebugContextEvent event) {
		updateFromContext(event.getContext());
	}

	@Override
	public void handleDebugEvents(DebugEvent[] events) {
		for (DebugEvent event : events) {
			if (event.getKind() == DebugEvent.SUSPEND || event.getKind() == DebugEvent.CHANGE) {
				JopDebugTarget debugTarget = null;
				Object source = event.getSource();
				if (source instanceof JopThread thread) {
					debugTarget = (JopDebugTarget) thread.getDebugTarget();
				} else if (source instanceof JopDebugTarget target) {
					debugTarget = target;
				}
				if (debugTarget != null) {
					final JopDebugTarget dt = debugTarget;
					Display.getDefault().asyncExec(() -> {
						if (!tableViewer.getControl().isDisposed()) {
							updateFromTarget(dt);
						}
					});
				}
				break;
			}
		}
	}

	private void updateFromContext(ISelection context) {
		if (tableViewer.getControl().isDisposed()) return;

		JopDebugTarget debugTarget = extractJopDebugTarget(context);
		if (debugTarget == null) {
			Display.getDefault().asyncExec(() -> {
				if (!tableViewer.getControl().isDisposed()) {
					tableViewer.setInput(new StackEntry[0]);
				}
			});
			return;
		}

		updateFromTarget(debugTarget);
	}

	private void updateFromTarget(JopDebugTarget debugTarget) {
		if (tableViewer.getControl().isDisposed()) return;

		IJopTarget target = debugTarget.getTarget();
		try {
			JopStackData stackData = target.readStack();
			JopRegisters regs = target.readRegisters();
			StackEntry[] entries = buildEntries(stackData, regs);

			if (Display.getCurrent() != null) {
				tableViewer.setInput(entries);
			} else {
				Display.getDefault().asyncExec(() -> {
					if (!tableViewer.getControl().isDisposed()) {
						tableViewer.setInput(entries);
					}
				});
			}
		} catch (JopTargetException e) {
			// Ignore
		}
	}

	private JopDebugTarget extractJopDebugTarget(ISelection context) {
		if (context instanceof IStructuredSelection sel && !sel.isEmpty()) {
			Object element = sel.getFirstElement();
			if (element instanceof JopDebugTarget target) {
				return target;
			}
			if (element instanceof JopThread thread) {
				return (JopDebugTarget) thread.getDebugTarget();
			}
			if (element instanceof JopStackFrame frame) {
				return (JopDebugTarget) frame.getDebugTarget();
			}
			if (element instanceof ILaunch launch) {
				for (IDebugTarget dt : launch.getDebugTargets()) {
					if (dt instanceof JopDebugTarget jdt) {
						return jdt;
					}
				}
			}
		}
		return null;
	}

	private StackEntry[] buildEntries(JopStackData stackData, JopRegisters regs) {
		int sp = regs.sp();
		int vp = regs.vp();
		int count = stackData.values().length;
		StackEntry[] entries = new StackEntry[count];

		for (int i = 0; i < count; i++) {
			StringBuilder annotation = new StringBuilder();
			if (i == sp) {
				annotation.append("-> SP (B next on stack)");
			}
			if (i == vp) {
				if (annotation.length() > 0) annotation.append(", ");
				annotation.append("VP base");
			}

			entries[i] = new StackEntry(i, stackData.values()[i], annotation.toString());
		}

		return entries;
	}

	@Override
	public void setFocus() {
		tableViewer.getControl().setFocus();
	}

	@Override
	public void dispose() {
		IDebugContextService contextService = DebugUITools.getDebugContextManager()
				.getContextService(getSite().getWorkbenchWindow());
		contextService.removeDebugContextListener(this);
		DebugPlugin.getDefault().removeDebugEventListener(this);
		super.dispose();
	}

	private static class StackEntry {
		final int index;
		final int value;
		final String annotation;

		StackEntry(int index, int value, String annotation) {
			this.index = index;
			this.value = value;
			this.annotation = annotation;
		}
	}
}
