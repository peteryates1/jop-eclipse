package com.jopdesign.ui.views;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;

import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.microcode.debug.JopDebugTarget;
import com.jopdesign.microcode.debug.JopStackFrame;
import com.jopdesign.microcode.debug.JopThread;

/**
 * View showing all JOP processor registers in a table.
 * Updates on debug context changes (stepping, breakpoint hits).
 */
public class JopRegistersView extends ViewPart implements IDebugContextListener, IDebugEventSetListener {

	public static final String VIEW_ID = "com.jopdesign.ui.views.registersView";

	private TableViewer tableViewer;
	private JopRegisters previousRegisters;
	private Color changedColor;

	private static final String[] REGISTER_NAMES = {
		"A (TOS)", "B (NOS)", "pc", "sp", "vp", "ar", "jpc",
		"mulA", "mulB", "mulResult",
		"memReadAddr", "memWriteAddr", "memWriteData", "memReadData"
	};

	@Override
	public void createPartControl(Composite parent) {
		changedColor = new Color(Display.getCurrent(), 255, 0, 0);

		tableViewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		Table table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		TableViewerColumn nameCol = new TableViewerColumn(tableViewer, SWT.NONE);
		nameCol.getColumn().setText("Register");
		nameCol.getColumn().setWidth(120);
		nameCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((RegisterEntry) element).name;
			}
		});

		TableViewerColumn decCol = new TableViewerColumn(tableViewer, SWT.NONE);
		decCol.getColumn().setText("Decimal");
		decCol.getColumn().setWidth(120);
		decCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				RegisterEntry entry = (RegisterEntry) element;
				if (entry.isLong) {
					return Long.toString(entry.longValue);
				}
				return Integer.toString(entry.value);
			}

			@Override
			public Color getForeground(Object element) {
				return ((RegisterEntry) element).changed ? changedColor : null;
			}
		});

		TableViewerColumn hexCol = new TableViewerColumn(tableViewer, SWT.NONE);
		hexCol.getColumn().setText("Hex");
		hexCol.getColumn().setWidth(120);
		hexCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				RegisterEntry entry = (RegisterEntry) element;
				if (entry.isLong) {
					return String.format("0x%016X", entry.longValue);
				}
				return String.format("0x%08X", entry.value);
			}

			@Override
			public Color getForeground(Object element) {
				return ((RegisterEntry) element).changed ? changedColor : null;
			}
		});

		tableViewer.setContentProvider(ArrayContentProvider.getInstance());

		// Register for debug context changes
		IDebugContextService contextService = DebugUITools.getDebugContextManager()
				.getContextService(getSite().getWorkbenchWindow());
		contextService.addDebugContextListener(this);

		// Register for debug events (step, suspend)
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
				Display.getDefault().asyncExec(() -> {
					if (!tableViewer.getControl().isDisposed()) {
						IDebugContextService contextService = DebugUITools.getDebugContextManager()
								.getContextService(getSite().getWorkbenchWindow());
						updateFromContext(contextService.getActiveContext());
					}
				});
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
					tableViewer.setInput(new RegisterEntry[0]);
				}
			});
			return;
		}

		IJopTarget target = debugTarget.getTarget();
		try {
			JopRegisters regs = target.readRegisters();
			RegisterEntry[] entries = buildEntries(regs);
			previousRegisters = regs;

			Display.getDefault().asyncExec(() -> {
				if (!tableViewer.getControl().isDisposed()) {
					tableViewer.setInput(entries);
				}
			});
		} catch (JopTargetException e) {
			// Ignore - target may be terminated
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
		}
		return null;
	}

	private RegisterEntry[] buildEntries(JopRegisters regs) {
		int[] values = {
			regs.a(), regs.b(), regs.pc(), regs.sp(), regs.vp(), regs.ar(), regs.jpc(),
			regs.mulA(), regs.mulB(), 0 /* placeholder for mulResult */,
			regs.memReadAddr(), regs.memWriteAddr(), regs.memWriteData(), regs.memReadData()
		};

		int[] prevValues = null;
		long prevMulResult = 0;
		if (previousRegisters != null) {
			prevValues = new int[] {
				previousRegisters.a(), previousRegisters.b(), previousRegisters.pc(),
				previousRegisters.sp(), previousRegisters.vp(), previousRegisters.ar(),
				previousRegisters.jpc(), previousRegisters.mulA(), previousRegisters.mulB(),
				0, previousRegisters.memReadAddr(), previousRegisters.memWriteAddr(),
				previousRegisters.memWriteData(), previousRegisters.memReadData()
			};
			prevMulResult = previousRegisters.mulResult();
		}

		RegisterEntry[] entries = new RegisterEntry[REGISTER_NAMES.length];
		for (int i = 0; i < REGISTER_NAMES.length; i++) {
			if (i == 9) {
				// mulResult is long
				boolean changed = previousRegisters != null && regs.mulResult() != prevMulResult;
				entries[i] = new RegisterEntry(REGISTER_NAMES[i], 0, regs.mulResult(), true, changed);
			} else {
				boolean changed = prevValues != null && values[i] != prevValues[i];
				entries[i] = new RegisterEntry(REGISTER_NAMES[i], values[i], 0L, false, changed);
			}
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
		if (changedColor != null && !changedColor.isDisposed()) {
			changedColor.dispose();
		}
		super.dispose();
	}

	private static class RegisterEntry {
		final String name;
		final int value;
		final long longValue;
		final boolean isLong;
		final boolean changed;

		RegisterEntry(String name, int value, long longValue, boolean isLong, boolean changed) {
			this.name = name;
			this.value = value;
			this.longValue = longValue;
			this.isLong = isLong;
			this.changed = changed;
		}
	}
}
