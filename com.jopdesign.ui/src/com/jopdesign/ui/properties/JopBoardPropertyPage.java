package com.jopdesign.ui.properties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;

import com.jopdesign.core.board.BoardDefinition;
import com.jopdesign.core.board.BoardRegistry;
import com.jopdesign.core.io.PeripheralDefinition;
import com.jopdesign.core.io.PeripheralRegister;
import com.jopdesign.core.io.PeripheralRegistry;
import com.jopdesign.core.preferences.JopPreferences;
import com.jopdesign.core.preferences.JopProjectPreferences;

/**
 * Project property page for JOP board / hardware configuration.
 *
 * <p>Provides a board preset dropdown that populates sensible defaults,
 * plus editable fields for all JopConfig hardware parameters.
 * Selecting a different board updates the fields to that board's defaults.
 */
public class JopBoardPropertyPage extends PropertyPage {

	private Combo boardCombo;
	private Label fpgaFamilyLabel;
	private Label fpgaDeviceLabel;
	private Label synthToolLabel;
	private Label clockLabel;

	private Combo memoryTypeCombo;
	private Combo bootModeCombo;

	private Spinner methodCacheSizeSpinner;
	private Spinner stackBufferSizeSpinner;
	private Button useOcacheCheck;
	private Spinner ocacheWayBitsSpinner;
	private Button useAcacheCheck;
	private Spinner acacheWayBitsSpinner;

	private Button enableMultiCoreCheck;
	private Spinner cpuCountSpinner;
	private Combo arbiterTypeCombo;
	private Button enableDebugCheck;

	private Map<String, Button> peripheralChecks = new LinkedHashMap<>();
	private Map<String, Combo> peripheralSlotCombos = new LinkedHashMap<>();
	private Label ioConflictLabel;
	private Text addressMapText;

	private Text generatedConfigText;

	private List<String> boardIds;

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		boardIds = BoardRegistry.getBoardIds();
		IEclipsePreferences prefs = JopProjectPreferences.forProject(getProject());

		// ---- Board selection ----
		Group boardGroup = createGroup(composite, "Board Selection");

		addLabel(boardGroup, "Target Board:");
		boardCombo = new Combo(boardGroup, SWT.READ_ONLY);
		boardCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		for (String name : BoardRegistry.getBoardNames()) {
			boardCombo.add(name);
		}
		boardCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				applyBoardDefaults();
				updateConfigPreview();
			}
		});

		// ---- FPGA info (read-only) ----
		Group fpgaGroup = createGroup(composite, "FPGA");

		addLabel(fpgaGroup, "Family:");
		fpgaFamilyLabel = new Label(fpgaGroup, SWT.NONE);
		fpgaFamilyLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		addLabel(fpgaGroup, "Device:");
		fpgaDeviceLabel = new Label(fpgaGroup, SWT.NONE);
		fpgaDeviceLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		addLabel(fpgaGroup, "Synth Tool:");
		synthToolLabel = new Label(fpgaGroup, SWT.NONE);
		synthToolLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		addLabel(fpgaGroup, "Clock:");
		clockLabel = new Label(fpgaGroup, SWT.NONE);
		clockLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// ---- Memory ----
		Group memGroup = createGroup(composite, "Memory");

		addLabel(memGroup, "Memory Type:");
		memoryTypeCombo = new Combo(memGroup, SWT.READ_ONLY);
		memoryTypeCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		memoryTypeCombo.setItems("bram", "sdram", "ddr3");
		memoryTypeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateConfigPreview();
			}
		});

		addLabel(memGroup, "Boot Mode:");
		bootModeCombo = new Combo(memGroup, SWT.READ_ONLY);
		bootModeCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		bootModeCombo.setItems("bram", "serial");
		bootModeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateConfigPreview();
			}
		});

		// ---- Cache configuration ----
		Group cacheGroup = createGroup(composite, "Cache Configuration");

		addLabel(cacheGroup, "Method Cache (bytes):");
		methodCacheSizeSpinner = new Spinner(cacheGroup, SWT.BORDER);
		methodCacheSizeSpinner.setMinimum(256);
		methodCacheSizeSpinner.setMaximum(16384);
		methodCacheSizeSpinner.setIncrement(1024);
		methodCacheSizeSpinner.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		addLabel(cacheGroup, "Stack Buffer (words):");
		stackBufferSizeSpinner = new Spinner(cacheGroup, SWT.BORDER);
		stackBufferSizeSpinner.setMinimum(8);
		stackBufferSizeSpinner.setMaximum(256);
		stackBufferSizeSpinner.setIncrement(16);
		stackBufferSizeSpinner.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		addLabel(cacheGroup, "Object Cache:");
		useOcacheCheck = new Button(cacheGroup, SWT.CHECK);
		useOcacheCheck.setText("Enable object field cache");
		useOcacheCheck.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ocacheWayBitsSpinner.setEnabled(useOcacheCheck.getSelection());
				updateConfigPreview();
			}
		});

		addLabel(cacheGroup, "Object Cache Ways (2^n):");
		ocacheWayBitsSpinner = new Spinner(cacheGroup, SWT.BORDER);
		ocacheWayBitsSpinner.setMinimum(1);
		ocacheWayBitsSpinner.setMaximum(6);
		ocacheWayBitsSpinner.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		addLabel(cacheGroup, "Array Cache:");
		useAcacheCheck = new Button(cacheGroup, SWT.CHECK);
		useAcacheCheck.setText("Enable array element cache");
		useAcacheCheck.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				acacheWayBitsSpinner.setEnabled(useAcacheCheck.getSelection());
				updateConfigPreview();
			}
		});

		addLabel(cacheGroup, "Array Cache Ways (2^n):");
		acacheWayBitsSpinner = new Spinner(cacheGroup, SWT.BORDER);
		acacheWayBitsSpinner.setMinimum(1);
		acacheWayBitsSpinner.setMaximum(6);
		acacheWayBitsSpinner.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// ---- Multi-Core / CMP ----
		Group cmpGroup = createGroup(composite, "Multi-Core / CMP");

		addLabel(cmpGroup, "Multi-Core:");
		enableMultiCoreCheck = new Button(cmpGroup, SWT.CHECK);
		enableMultiCoreCheck.setText("Enable CMP support");
		enableMultiCoreCheck.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean enabled = enableMultiCoreCheck.getSelection();
				cpuCountSpinner.setEnabled(enabled);
				arbiterTypeCombo.setEnabled(enabled);
				updateConfigPreview();
			}
		});

		addLabel(cmpGroup, "CPU Cores:");
		cpuCountSpinner = new Spinner(cmpGroup, SWT.BORDER);
		cpuCountSpinner.setMinimum(1);
		cpuCountSpinner.setMaximum(8);
		cpuCountSpinner.setIncrement(1);
		cpuCountSpinner.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		addLabel(cmpGroup, "Arbiter:");
		arbiterTypeCombo = new Combo(cmpGroup, SWT.READ_ONLY);
		arbiterTypeCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		arbiterTypeCombo.setItems("tdma", "priority", "roundrobin");
		arbiterTypeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateConfigPreview();
			}
		});

		addLabel(cmpGroup, "Debug:");
		enableDebugCheck = new Button(cmpGroup, SWT.CHECK);
		enableDebugCheck.setText("Enable debug instrumentation");
		enableDebugCheck.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateConfigPreview();
			}
		});

		// ---- IO Peripherals ----
		Group ioGroup = createGroup(composite, "IO Peripherals");

		// Fixed peripherals (read-only info)
		for (PeripheralDefinition p : PeripheralRegistry.getFixedPeripherals()) {
			addLabel(ioGroup, p.name() + ":");
			Label fixedLabel = new Label(ioGroup, SWT.NONE);
			fixedLabel.setText("Slot " + p.defaultSlot() + " (fixed) — "
					+ p.addressRange(p.defaultSlot()));
			fixedLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		}

		// Optional peripherals (checkboxes + slot combos)
		for (PeripheralDefinition p : PeripheralRegistry.getOptionalPeripherals()) {
			Button check = new Button(ioGroup, SWT.CHECK);
			check.setText(p.name());
			check.setToolTipText(p.description());
			peripheralChecks.put(p.id(), check);

			Composite slotRow = new Composite(ioGroup, SWT.NONE);
			slotRow.setLayout(new GridLayout(2, false));
			slotRow.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			Label slotLabel = new Label(slotRow, SWT.NONE);
			slotLabel.setText("Slot:");
			Combo slotCombo = new Combo(slotRow, SWT.READ_ONLY);
			slotCombo.setItems("2", "3");
			slotCombo.select(p.defaultSlot() - 2);
			slotCombo.setEnabled(false);
			peripheralSlotCombos.put(p.id(), slotCombo);

			check.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					slotCombo.setEnabled(check.getSelection());
					validateIoSlots();
					updateAddressMap();
					updateConfigPreview();
				}
			});
			slotCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					validateIoSlots();
					updateAddressMap();
					updateConfigPreview();
				}
			});
		}

		ioConflictLabel = new Label(ioGroup, SWT.NONE);
		ioConflictLabel.setForeground(
				parent.getDisplay().getSystemColor(SWT.COLOR_RED));
		GridData conflictGd = new GridData(GridData.FILL_HORIZONTAL);
		conflictGd.horizontalSpan = 2;
		ioConflictLabel.setLayoutData(conflictGd);

		// ---- IO Address Map ----
		Group addrMapGroup = createGroup(composite, "IO Address Map");

		addressMapText = new Text(addrMapGroup,
				SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
		addressMapText.setFont(parent.getDisplay().getSystemFont());
		GridData addrGd = new GridData(GridData.FILL_BOTH);
		addrGd.horizontalSpan = 2;
		addrGd.heightHint = 100;
		addressMapText.setLayoutData(addrGd);

		// ---- Generated config preview ----
		Group previewGroup = createGroup(composite, "SpinalHDL Config Preview");

		generatedConfigText = new Text(previewGroup, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL);
		GridData previewGd = new GridData(GridData.FILL_BOTH);
		previewGd.horizontalSpan = 2;
		previewGd.heightHint = 120;
		generatedConfigText.setLayoutData(previewGd);

		// ---- Load saved values ----
		loadFromPreferences(prefs);

		return composite;
	}

	private void loadFromPreferences(IEclipsePreferences prefs) {
		String boardId = JopProjectPreferences.get(getProject(),
				JopPreferences.BOARD_ID, "qmtech-ep4cgx150-bram");

		// Select the board in dropdown
		int boardIndex = boardIds.indexOf(boardId);
		if (boardIndex >= 0) {
			boardCombo.select(boardIndex);
		} else if (boardCombo.getItemCount() > 0) {
			boardCombo.select(0);
		}

		// Load hardware params (use saved values, not board defaults)
		String memType = JopProjectPreferences.get(getProject(),
				JopPreferences.MEMORY_TYPE, "bram");
		selectComboItem(memoryTypeCombo, memType);

		String bootMode = JopProjectPreferences.get(getProject(),
				JopPreferences.BOOT_MODE, "bram");
		selectComboItem(bootModeCombo, bootMode);

		methodCacheSizeSpinner.setSelection(
				getIntPref(JopPreferences.METHOD_CACHE_SIZE, 4096));
		stackBufferSizeSpinner.setSelection(
				getIntPref(JopPreferences.STACK_BUFFER_SIZE, 64));
		useOcacheCheck.setSelection(
				getBoolPref(JopPreferences.USE_OCACHE, true));
		ocacheWayBitsSpinner.setSelection(
				getIntPref(JopPreferences.OCACHE_WAY_BITS, 4));
		ocacheWayBitsSpinner.setEnabled(useOcacheCheck.getSelection());
		useAcacheCheck.setSelection(
				getBoolPref(JopPreferences.USE_ACACHE, true));
		acacheWayBitsSpinner.setSelection(
				getIntPref(JopPreferences.ACACHE_WAY_BITS, 4));
		acacheWayBitsSpinner.setEnabled(useAcacheCheck.getSelection());

		// CMP settings
		enableMultiCoreCheck.setSelection(
				getBoolPref(JopPreferences.ENABLE_MULTI_CORE, false));
		cpuCountSpinner.setSelection(
				getIntPref(JopPreferences.CPU_COUNT, 1));
		cpuCountSpinner.setEnabled(enableMultiCoreCheck.getSelection());
		String arbiter = JopProjectPreferences.get(getProject(),
				JopPreferences.ARBITER_TYPE, "tdma");
		selectComboItem(arbiterTypeCombo, arbiter);
		arbiterTypeCombo.setEnabled(enableMultiCoreCheck.getSelection());
		enableDebugCheck.setSelection(
				getBoolPref(JopPreferences.ENABLE_DEBUG, false));

		// IO peripherals
		String enabledPeripherals = JopProjectPreferences.get(getProject(),
				JopPreferences.IO_PERIPHERALS, "");
		List<String> enabledList = new ArrayList<>();
		if (!enabledPeripherals.isEmpty()) {
			for (String id : enabledPeripherals.split(",")) {
				enabledList.add(id.trim());
			}
		}
		for (Map.Entry<String, Button> entry : peripheralChecks.entrySet()) {
			boolean enabled = enabledList.contains(entry.getKey());
			entry.getValue().setSelection(enabled);
			Combo slotCombo = peripheralSlotCombos.get(entry.getKey());
			slotCombo.setEnabled(enabled);
			String slotVal = JopProjectPreferences.get(getProject(),
					JopPreferences.IO_SLOT_PREFIX + entry.getKey(), "");
			if (!slotVal.isEmpty()) {
				try {
					int slot = Integer.parseInt(slotVal);
					if (slot >= 2 && slot <= 3) {
						slotCombo.select(slot - 2);
					}
				} catch (NumberFormatException e) {
					// use default
				}
			}
		}

		// Update read-only FPGA info
		updateFpgaInfo();
		validateIoSlots();
		updateAddressMap();
		updateConfigPreview();
	}

	/**
	 * When the user selects a different board, populate the fields
	 * with that board's default parameters.
	 */
	private void applyBoardDefaults() {
		int idx = boardCombo.getSelectionIndex();
		if (idx < 0 || idx >= boardIds.size()) return;

		BoardDefinition board = BoardRegistry.getBoard(boardIds.get(idx));
		if (board == null) return;

		selectComboItem(memoryTypeCombo, board.memoryType());
		selectComboItem(bootModeCombo, board.bootMode());
		methodCacheSizeSpinner.setSelection(board.methodCacheSize());
		stackBufferSizeSpinner.setSelection(board.stackBufferSize());
		useOcacheCheck.setSelection(board.useOcache());
		ocacheWayBitsSpinner.setSelection(board.ocacheWayBits());
		ocacheWayBitsSpinner.setEnabled(board.useOcache());
		useAcacheCheck.setSelection(board.useAcache());
		acacheWayBitsSpinner.setSelection(board.acacheWayBits());
		acacheWayBitsSpinner.setEnabled(board.useAcache());
		enableMultiCoreCheck.setSelection(board.enableMultiCore());
		cpuCountSpinner.setSelection(board.cpuCount());
		cpuCountSpinner.setEnabled(board.enableMultiCore());
		arbiterTypeCombo.setEnabled(board.enableMultiCore());
		if (board.enableMultiCore()) {
			selectComboItem(arbiterTypeCombo, "tdma");
		}
		enableDebugCheck.setSelection(board.enableDebug());

		updateFpgaInfo();
	}

	private void updateFpgaInfo() {
		int idx = boardCombo.getSelectionIndex();
		if (idx < 0 || idx >= boardIds.size()) return;

		BoardDefinition board = BoardRegistry.getBoard(boardIds.get(idx));
		if (board == null) return;

		fpgaFamilyLabel.setText(board.fpgaFamily());
		fpgaDeviceLabel.setText(board.fpgaDevice());
		synthToolLabel.setText(board.synthTool().isEmpty() ? "(none)" :
				board.synthTool().substring(0, 1).toUpperCase() + board.synthTool().substring(1));
		clockLabel.setText(board.clockInputMhz() + " MHz input / "
				+ board.systemClockMhz() + " MHz system");
	}

	private void updateConfigPreview() {
		String config = generateScalaConfig();
		generatedConfigText.setText(config);
	}

	private String generateScalaConfig() {
		int idx = boardCombo.getSelectionIndex();
		String boardName = idx >= 0 && idx < boardIds.size() ? boardIds.get(idx) : "custom";

		StringBuilder sb = new StringBuilder();
		sb.append("// JopConfig for ").append(boardName).append('\n');
		sb.append("JopConfig(\n");
		sb.append("  methodCacheSize = ").append(methodCacheSizeSpinner.getSelection()).append(",\n");
		sb.append("  stackBufferSize = ").append(stackBufferSizeSpinner.getSelection()).append(",\n");
		sb.append("  useOcache = ").append(useOcacheCheck.getSelection()).append(",\n");
		if (useOcacheCheck.getSelection()) {
			sb.append("  ocacheWayBits = ").append(ocacheWayBitsSpinner.getSelection()).append(",\n");
		}
		sb.append("  useAcache = ").append(useAcacheCheck.getSelection()).append(",\n");
		if (useAcacheCheck.getSelection()) {
			sb.append("  acacheWayBits = ").append(acacheWayBitsSpinner.getSelection()).append(",\n");
		}

		BoardDefinition board = idx >= 0 && idx < boardIds.size()
				? BoardRegistry.getBoard(boardIds.get(idx)) : null;
		if (board != null && !board.fpgaTarget().isEmpty()) {
			String target = board.fpgaTarget().equals("xilinx") ? "Xilinx" : "Altera";
			sb.append("  fpgaTarget = FpgaTarget.").append(target).append(",\n");
		}

		sb.append("  enableCache = ").append(
				useOcacheCheck.getSelection() || useAcacheCheck.getSelection()).append(",\n");
		sb.append("  enableMultiCore = ").append(enableMultiCoreCheck.getSelection()).append(",\n");
		if (enableMultiCoreCheck.getSelection()) {
			sb.append("  cpuCnt = ").append(cpuCountSpinner.getSelection()).append(",\n");
		}
		sb.append("  enableDebug = ").append(enableDebugCheck.getSelection());

		// IO peripherals
		List<String> ioLines = new ArrayList<>();
		for (Map.Entry<String, Button> entry : peripheralChecks.entrySet()) {
			if (entry.getValue().getSelection()) {
				PeripheralDefinition p = PeripheralRegistry.getPeripheral(entry.getKey());
				if (p == null) continue;
				Combo slotCombo = peripheralSlotCombos.get(entry.getKey());
				int slot = slotCombo.getSelectionIndex() + 2;
				ioLines.add("  // IO slot " + slot + ": " + p.name()
						+ " (" + p.spinalClassName() + ")");
			}
		}
		if (!ioLines.isEmpty()) {
			sb.append(",\n");
			sb.append(String.join("\n", ioLines));
		}

		sb.append('\n');
		sb.append(")");
		return sb.toString();
	}

	@Override
	public boolean performOk() {
		IEclipsePreferences prefs = JopProjectPreferences.forProject(getProject());

		int idx = boardCombo.getSelectionIndex();
		if (idx >= 0 && idx < boardIds.size()) {
			prefs.put(JopPreferences.BOARD_ID, boardIds.get(idx));
			prefs.put(JopPreferences.BOARD_TARGET, boardIds.get(idx));
		}

		prefs.put(JopPreferences.MEMORY_TYPE, memoryTypeCombo.getText());
		prefs.put(JopPreferences.BOOT_MODE, bootModeCombo.getText());
		prefs.putInt(JopPreferences.METHOD_CACHE_SIZE, methodCacheSizeSpinner.getSelection());
		prefs.putInt(JopPreferences.STACK_BUFFER_SIZE, stackBufferSizeSpinner.getSelection());
		prefs.putBoolean(JopPreferences.USE_OCACHE, useOcacheCheck.getSelection());
		prefs.putInt(JopPreferences.OCACHE_WAY_BITS, ocacheWayBitsSpinner.getSelection());
		prefs.putBoolean(JopPreferences.USE_ACACHE, useAcacheCheck.getSelection());
		prefs.putInt(JopPreferences.ACACHE_WAY_BITS, acacheWayBitsSpinner.getSelection());
		prefs.putBoolean(JopPreferences.ENABLE_MULTI_CORE, enableMultiCoreCheck.getSelection());
		prefs.putInt(JopPreferences.CPU_COUNT, cpuCountSpinner.getSelection());
		prefs.put(JopPreferences.ARBITER_TYPE, arbiterTypeCombo.getText());
		prefs.putBoolean(JopPreferences.ENABLE_DEBUG, enableDebugCheck.getSelection());

		// IO peripherals
		List<String> enabledIds = new ArrayList<>();
		for (Map.Entry<String, Button> entry : peripheralChecks.entrySet()) {
			if (entry.getValue().getSelection()) {
				enabledIds.add(entry.getKey());
				Combo slotCombo = peripheralSlotCombos.get(entry.getKey());
				int slot = slotCombo.getSelectionIndex() + 2;
				prefs.putInt(JopPreferences.IO_SLOT_PREFIX + entry.getKey(), slot);
			}
		}
		prefs.put(JopPreferences.IO_PERIPHERALS, String.join(",", enabledIds));

		try {
			prefs.flush();
		} catch (Exception e) {
			// Log but don't fail
		}
		return super.performOk();
	}

	@Override
	protected void performDefaults() {
		if (boardCombo.getItemCount() > 0) {
			boardCombo.select(0);
			applyBoardDefaults();
		}
		// Reset IO peripherals to none enabled
		for (Map.Entry<String, Button> entry : peripheralChecks.entrySet()) {
			entry.getValue().setSelection(false);
			Combo slotCombo = peripheralSlotCombos.get(entry.getKey());
			slotCombo.setEnabled(false);
			PeripheralDefinition p = PeripheralRegistry.getPeripheral(entry.getKey());
			if (p != null) {
				slotCombo.select(p.defaultSlot() - 2);
			}
		}
		validateIoSlots();
		updateAddressMap();
		updateConfigPreview();
		super.performDefaults();
	}

	// ---- IO Peripheral helpers ----

	private void validateIoSlots() {
		Map<Integer, List<String>> slotUsers = new LinkedHashMap<>();
		for (Map.Entry<String, Button> entry : peripheralChecks.entrySet()) {
			if (entry.getValue().getSelection()) {
				Combo slotCombo = peripheralSlotCombos.get(entry.getKey());
				int slot = slotCombo.getSelectionIndex() + 2;
				slotUsers.computeIfAbsent(slot, k -> new ArrayList<>())
						.add(entry.getKey());
			}
		}
		StringBuilder conflicts = new StringBuilder();
		for (Map.Entry<Integer, List<String>> entry : slotUsers.entrySet()) {
			if (entry.getValue().size() > 1) {
				if (conflicts.length() > 0) conflicts.append("; ");
				conflicts.append("Slot ").append(entry.getKey())
						.append(" conflict: ");
				List<String> names = new ArrayList<>();
				for (String id : entry.getValue()) {
					PeripheralDefinition p = PeripheralRegistry.getPeripheral(id);
					names.add(p != null ? p.name() : id);
				}
				conflicts.append(String.join(", ", names));
			}
		}
		ioConflictLabel.setText(conflicts.toString());
		setValid(conflicts.length() == 0);
	}

	private void updateAddressMap() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%-14s %-20s %s%n", "Address", "Peripheral", "Registers"));
		sb.append(String.format("%-14s %-20s %s%n",
				"──────────────", "────────────────────", "──────────────────"));

		// Fixed peripherals first
		for (PeripheralDefinition p : PeripheralRegistry.getFixedPeripherals()) {
			sb.append(String.format("%-14s %-20s",
					p.addressRange(p.defaultSlot()), p.name()));
			appendRegisterSummary(sb, p);
			sb.append('\n');
		}

		// Enabled optional peripherals
		for (Map.Entry<String, Button> entry : peripheralChecks.entrySet()) {
			if (entry.getValue().getSelection()) {
				PeripheralDefinition p = PeripheralRegistry.getPeripheral(entry.getKey());
				if (p == null) continue;
				Combo slotCombo = peripheralSlotCombos.get(entry.getKey());
				int slot = slotCombo.getSelectionIndex() + 2;
				sb.append(String.format("%-14s %-20s",
						p.addressRange(slot), p.name()));
				appendRegisterSummary(sb, p);
				sb.append('\n');
			}
		}

		// Show unused slots
		boolean[] used = new boolean[4];
		used[0] = true; // sys
		used[1] = true; // uart
		for (Map.Entry<String, Button> entry : peripheralChecks.entrySet()) {
			if (entry.getValue().getSelection()) {
				Combo slotCombo = peripheralSlotCombos.get(entry.getKey());
				used[slotCombo.getSelectionIndex() + 2] = true;
			}
		}
		for (int i = 2; i <= 3; i++) {
			if (!used[i]) {
				sb.append(String.format("%-14s %-20s (available)%n",
						String.format("0x%02X-0x%02X", i * 16, i * 16 + 15),
						"—"));
			}
		}

		addressMapText.setText(sb.toString());
	}

	private void appendRegisterSummary(StringBuilder sb, PeripheralDefinition p) {
		if (p.registers().isEmpty()) return;
		sb.append(' ');
		boolean first = true;
		for (PeripheralRegister r : p.registers()) {
			if (!first) sb.append(", ");
			sb.append(r.name());
			first = false;
			if (sb.length() % 120 > 80) {
				sb.append("...");
				break;
			}
		}
	}

	// ---- Helpers ----

	private Group createGroup(Composite parent, String title) {
		Group group = new Group(parent, SWT.NONE);
		group.setText(title);
		group.setLayout(new GridLayout(2, false));
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		group.setLayoutData(gd);
		return group;
	}

	private void addLabel(Composite parent, String text) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(text);
	}

	private void selectComboItem(Combo combo, String value) {
		String[] items = combo.getItems();
		for (int i = 0; i < items.length; i++) {
			if (items[i].equals(value)) {
				combo.select(i);
				return;
			}
		}
		if (combo.getItemCount() > 0) {
			combo.select(0);
		}
	}

	private int getIntPref(String key, int defaultValue) {
		String val = JopProjectPreferences.get(getProject(), key, "");
		if (val.isEmpty()) return defaultValue;
		try {
			return Integer.parseInt(val);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private boolean getBoolPref(String key, boolean defaultValue) {
		String val = JopProjectPreferences.get(getProject(), key, "");
		if (val.isEmpty()) return defaultValue;
		return Boolean.parseBoolean(val);
	}

	private IProject getProject() {
		IAdaptable element = getElement();
		if (element instanceof IProject project) {
			return project;
		}
		return element.getAdapter(IProject.class);
	}
}
