package com.jopdesign.ui.views;

import java.util.List;

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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;

import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.JopRegisters;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.core.sim.JopTargetInfo;
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

	private static final String[] ARCH_REGISTER_NAMES = {
		"A (TOS)", "B (NOS)", "pc", "sp", "vp", "ar", "jpc"
	};
	private static final String[] DEBUG_REGISTER_NAMES = {
		"mulResult", "memReadAddr", "memWriteAddr", "memWriteData", "memReadData"
	};
	private static final String[] EXTENDED_REGISTER_NAMES = {
		"flags", "instr", "jopd"
	};

	@Override
	public void createPartControl(Composite parent) {
		changedColor = new Color(Display.getDefault(), 255, 0, 0);

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
				return entry.isSeparator ? "" : Integer.toString(entry.value);
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
				return entry.isSeparator ? "" : String.format("0x%08X", entry.value);
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
				// Extract target directly from event source (more reliable than context)
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
					tableViewer.setInput(new RegisterEntry[0]);
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
			JopRegisters regs = target.readRegisters();
			JopTargetInfo info = target.getTargetInfo();
			RegisterEntry[] entries = buildEntries(regs, info);
			previousRegisters = regs;

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

	private RegisterEntry[] buildEntries(JopRegisters regs, JopTargetInfo info) {
		int[] archValues = {
			regs.a(), regs.b(), regs.pc(), regs.sp(), regs.vp(), regs.ar(), regs.jpc()
		};
		int[] debugValues = {
			regs.mulResult(), regs.memReadAddr(), regs.memWriteAddr(), regs.memWriteData(), regs.memReadData()
		};
		int[] extValues = {
			regs.flags(), regs.instr(), regs.jopd()
		};

		int[] prevArchValues = null;
		int[] prevDebugValues = null;
		int[] prevExtValues = null;
		if (previousRegisters != null) {
			prevArchValues = new int[] {
				previousRegisters.a(), previousRegisters.b(), previousRegisters.pc(),
				previousRegisters.sp(), previousRegisters.vp(), previousRegisters.ar(),
				previousRegisters.jpc()
			};
			prevDebugValues = new int[] {
				previousRegisters.mulResult(), previousRegisters.memReadAddr(),
				previousRegisters.memWriteAddr(), previousRegisters.memWriteData(),
				previousRegisters.memReadData()
			};
			prevExtValues = new int[] {
				previousRegisters.flags(), previousRegisters.instr(), previousRegisters.jopd()
			};
		}

		boolean showExtended = info != null && info.extendedRegistersMask() != 0;
		int totalSize = 1 + archValues.length + 1 + debugValues.length;
		if (showExtended) {
			totalSize += 1 + extValues.length;
		}

		List<RegisterEntry> entries = new java.util.ArrayList<>(totalSize);

		// Architectural group
		entries.add(new RegisterEntry("--- Architectural ---", 0, false, true));
		for (int i = 0; i < ARCH_REGISTER_NAMES.length; i++) {
			boolean changed = prevArchValues != null && archValues[i] != prevArchValues[i];
			entries.add(new RegisterEntry(ARCH_REGISTER_NAMES[i], archValues[i], changed, false));
		}

		// Debug group
		entries.add(new RegisterEntry("--- Debug ---", 0, false, true));
		for (int i = 0; i < DEBUG_REGISTER_NAMES.length; i++) {
			boolean changed = prevDebugValues != null && debugValues[i] != prevDebugValues[i];
			entries.add(new RegisterEntry(DEBUG_REGISTER_NAMES[i], debugValues[i], changed, false));
		}

		// Extended group (only if target reports them)
		if (showExtended) {
			entries.add(new RegisterEntry("--- Extended ---", 0, false, true));
			for (int i = 0; i < EXTENDED_REGISTER_NAMES.length; i++) {
				boolean changed = prevExtValues != null && extValues[i] != prevExtValues[i];
				entries.add(new RegisterEntry(EXTENDED_REGISTER_NAMES[i], extValues[i], changed, false));
			}
		}

		return entries.toArray(new RegisterEntry[0]);
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
		final boolean changed;
		final boolean isSeparator;

		RegisterEntry(String name, int value, boolean changed, boolean isSeparator) {
			this.name = name;
			this.value = value;
			this.changed = changed;
			this.isSeparator = isSeparator;
		}
	}
}
