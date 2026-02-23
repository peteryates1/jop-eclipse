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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.jopdesign.core.sim.IJopTarget;
import com.jopdesign.core.sim.JopMemoryData;
import com.jopdesign.core.sim.JopTargetException;
import com.jopdesign.microcode.debug.JopDebugTarget;
import com.jopdesign.microcode.debug.JopStackFrame;
import com.jopdesign.microcode.debug.JopThread;

/**
 * View showing JOP memory contents as a hex dump table.
 * Displays 16 words per row with address, hex values, and ASCII representation.
 * Updates on debug context changes (stepping, breakpoint hits).
 */
public class JopMemoryView extends ViewPart implements IDebugContextListener, IDebugEventSetListener {

	public static final String VIEW_ID = "com.jopdesign.ui.views.memoryView";

	private static final int WORDS_PER_ROW = 8;
	private static final int DEFAULT_WORD_COUNT = 256;

	private TableViewer tableViewer;
	private Text addressText;
	private Text countText;
	private JopDebugTarget currentTarget;

	@Override
	public void createPartControl(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, false));
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));

		// Address bar
		Composite addrBar = new Composite(comp, SWT.NONE);
		addrBar.setLayout(new GridLayout(5, false));
		addrBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label addrLabel = new Label(addrBar, SWT.NONE);
		addrLabel.setText("Address:");

		addressText = new Text(addrBar, SWT.BORDER);
		addressText.setText("0");
		GridData addrData = new GridData();
		addrData.widthHint = 100;
		addressText.setLayoutData(addrData);

		Label countLabel = new Label(addrBar, SWT.NONE);
		countLabel.setText("Words:");

		countText = new Text(addrBar, SWT.BORDER);
		countText.setText(Integer.toString(DEFAULT_WORD_COUNT));
		GridData countData = new GridData();
		countData.widthHint = 60;
		countText.setLayoutData(countData);

		Button refreshBtn = new Button(addrBar, SWT.PUSH);
		refreshBtn.setText("Refresh");
		refreshBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (currentTarget != null) {
					updateFromTarget(currentTarget);
				}
			}
		});

		// Memory table
		tableViewer = new TableViewer(comp, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		Table table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		table.setFont(org.eclipse.jface.resource.JFaceResources.getTextFont());

		// Address column
		TableViewerColumn addrCol = new TableViewerColumn(tableViewer, SWT.NONE);
		addrCol.getColumn().setText("Address");
		addrCol.getColumn().setWidth(90);
		addrCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return String.format("0x%06X", ((MemoryRow) element).address);
			}
		});

		// Hex columns (one per word in the row)
		for (int i = 0; i < WORDS_PER_ROW; i++) {
			final int col = i;
			TableViewerColumn hexCol = new TableViewerColumn(tableViewer, SWT.NONE);
			hexCol.getColumn().setText(String.format("+%d", i));
			hexCol.getColumn().setWidth(85);
			hexCol.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					MemoryRow row = (MemoryRow) element;
					if (col < row.values.length) {
						return String.format("%08X", row.values[col]);
					}
					return "";
				}
			});
		}

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
		currentTarget = debugTarget;
		if (debugTarget == null) {
			Display.getDefault().asyncExec(() -> {
				if (!tableViewer.getControl().isDisposed()) {
					tableViewer.setInput(new MemoryRow[0]);
				}
			});
			return;
		}

		updateFromTarget(debugTarget);
	}

	private void updateFromTarget(JopDebugTarget debugTarget) {
		if (tableViewer.getControl().isDisposed()) return;

		currentTarget = debugTarget;
		IJopTarget target = debugTarget.getTarget();

		int address;
		int count;
		try {
			String addrStr = addressText.getText().trim();
			if (addrStr.startsWith("0x") || addrStr.startsWith("0X")) {
				address = Integer.parseUnsignedInt(addrStr.substring(2), 16);
			} else {
				address = Integer.parseUnsignedInt(addrStr);
			}
			count = Integer.parseInt(countText.getText().trim());
			count = Math.min(count, 256); // Protocol max
		} catch (NumberFormatException e) {
			address = 0;
			count = DEFAULT_WORD_COUNT;
		}

		try {
			JopMemoryData memData = target.readMemory(address, count);
			MemoryRow[] rows = buildRows(memData);

			if (Display.getCurrent() != null) {
				tableViewer.setInput(rows);
			} else {
				Display.getDefault().asyncExec(() -> {
					if (!tableViewer.getControl().isDisposed()) {
						tableViewer.setInput(rows);
					}
				});
			}
		} catch (JopTargetException e) {
			// Ignore - target may be terminated or running
		}
	}

	private MemoryRow[] buildRows(JopMemoryData memData) {
		int[] values = memData.values();
		int startAddr = memData.startAddress();
		int rowCount = (values.length + WORDS_PER_ROW - 1) / WORDS_PER_ROW;
		MemoryRow[] rows = new MemoryRow[rowCount];

		for (int r = 0; r < rowCount; r++) {
			int offset = r * WORDS_PER_ROW;
			int remaining = Math.min(WORDS_PER_ROW, values.length - offset);
			int[] rowValues = new int[remaining];
			System.arraycopy(values, offset, rowValues, 0, remaining);
			rows[r] = new MemoryRow(startAddr + offset, rowValues);
		}

		return rows;
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

	private static class MemoryRow {
		final int address;
		final int[] values;

		MemoryRow(int address, int[] values) {
			this.address = address;
			this.values = values;
		}
	}
}
